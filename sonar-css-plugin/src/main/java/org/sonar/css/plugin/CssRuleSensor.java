/*
 * SonarCSS
 * Copyright (C) 2018-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.css.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.css.plugin.CssRules.StylelintConfig;
import org.sonar.css.plugin.StylelintReport.Issue;
import org.sonar.css.plugin.StylelintReport.IssuesPerFile;
import org.sonar.css.plugin.bundle.BundleHandler;
import org.sonar.css.plugin.server.CssAnalyzerBridgeServer;
import org.sonar.css.plugin.server.CssAnalyzerBridgeServer.AnalysisRequest;
import org.sonar.css.plugin.server.exception.ServerAlreadyFailedException;
import org.sonarsource.analyzer.commons.ProgressReport;
import org.sonarsource.nodejs.NodeCommand;
import org.sonarsource.nodejs.NodeCommandException;

public class CssRuleSensor implements Sensor {

  private static final Logger LOG = Loggers.get(CssRuleSensor.class);

  private final BundleHandler bundleHandler;
  private final CssRules cssRules;
  // TODO remove LinterCommandProvider and only keep CssAnalyzerBridgeServer
  private final LinterCommandProvider linterCommandProvider;
  private final CssAnalyzerBridgeServer cssAnalyzerBridgeServer;
  @Nullable
  private final AnalysisWarningsWrapper analysisWarnings;


  public CssRuleSensor(
    BundleHandler bundleHandler,
    CheckFactory checkFactory,
    LinterCommandProvider linterCommandProvider,
    CssAnalyzerBridgeServer cssAnalyzerBridgeServer,
    @Nullable AnalysisWarningsWrapper analysisWarnings
  ) {
    this.bundleHandler = bundleHandler;
    this.linterCommandProvider = linterCommandProvider;
    this.cssRules = new CssRules(checkFactory);
    this.cssAnalyzerBridgeServer = cssAnalyzerBridgeServer;
    this.analysisWarnings = analysisWarnings;
  }

  public CssRuleSensor(
    BundleHandler bundleHandler,
    CheckFactory checkFactory,
    LinterCommandProvider linterCommandProvider,
    CssAnalyzerBridgeServer cssAnalyzerBridgeServer
  ) {
    this(bundleHandler, checkFactory, linterCommandProvider, cssAnalyzerBridgeServer,null);
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .name("SonarCSS Rules");
  }

  @Override
  public void execute(SensorContext context) {
    if (context.config().hasKey(CssPlugin.FORMER_NODE_EXECUTABLE)) {
      String msg = "Property '" + CssPlugin.FORMER_NODE_EXECUTABLE + "' is ignored, 'sonar.nodejs.executable' should be used instead";
      LOG.warn(msg);
      if (analysisWarnings != null) {
        analysisWarnings.addUnique(msg);
      }
    }

    if (cssRules.isEmpty()) {
      LOG.warn("No rules are activated in CSS Quality Profile");
      return;
    }

    boolean failFast = context.config().getBoolean("sonar.internal.analysis.failFast").orElse(false);
    try {
      List<InputFile> inputFiles = getInputFiles(context);
      if (!inputFiles.isEmpty()) {
        cssAnalyzerBridgeServer.startServerLazily(context);
        analyzeFiles(context, inputFiles);
      }
    } catch (CancellationException e) {
      // do not propagate the exception
      LOG.info(e.toString());
    } catch (ServerAlreadyFailedException e) {
      LOG.debug("Skipping start of stylelint-bridge server due to the failure during first analysis");
      LOG.debug("Skipping execution of stylelint-based rules due to the problems with stylelint-bridge server");
    } catch (NodeCommandException e) {
      LOG.error(e.getMessage(), e);
      if (analysisWarnings != null) {
        analysisWarnings.addUnique("CSS rules were not executed. " + e.getMessage());
      }
      if (failFast) {
        throw new IllegalStateException("Analysis failed (\"sonar.internal.analysis.failFast\"=true)", e);
      }
    } catch (Exception e) {
      LOG.error("Failure during analysis, " + cssAnalyzerBridgeServer.getCommandInfo(), e);
      if (failFast) {
        throw new IllegalStateException("Analysis failed (\"sonar.internal.analysis.failFast\"=true)", e);
      }
    }

    File deployDestination = context.fileSystem().workDir();

    try {
      bundleHandler.deployBundle(deployDestination);
      createLinterConfig(deployDestination);
      StringBuilder output = new StringBuilder();

      NodeCommand nodeCommand = linterCommandProvider.nodeCommand(deployDestination, context, output::append, LOG::error);
      LOG.debug("Starting process: " + nodeCommand.toString());
      nodeCommand.start();

      if (isSuccessful(nodeCommand.waitFor())) {
        saveIssues(context, output.toString());
      }
    } catch (NodeCommandException e) {
      LOG.error(e.getMessage() + " No CSS files will be analyzed.", e);
      if (analysisWarnings != null) {
        analysisWarnings.addUnique("CSS files were not analyzed. " + e.getMessage());
      }
    } catch (Exception e) {
      LOG.error("Failed to run external linting process", e);
    }
  }

  private void analyzeFiles(SensorContext context, List<InputFile> inputFiles) throws InterruptedException, IOException {
    ProgressReport progressReport = new ProgressReport("Analysis progress", TimeUnit.SECONDS.toMillis(10));
    boolean success = false;
    try {
      progressReport.start(inputFiles.stream().map(InputFile::toString).collect(Collectors.toList()));
      for (InputFile inputFile : inputFiles) {
        if (context.isCancelled()) {
          throw new CancellationException("Analysis interrupted because the SensorContext is in cancelled state");
        }
        if (cssAnalyzerBridgeServer.isAlive()) {
          analyzeFile(context, inputFile);
          progressReport.nextFile();
        } else {
          throw new IllegalStateException("stylelint-bridge server is not answering");
        }
      }
      success = true;
    } finally {
      if (success) {
        progressReport.stop();
      } else {
        progressReport.cancel();
      }
      progressReport.join();
    }
  }

  private void analyzeFile(SensorContext context, InputFile inputFile) throws IOException {
    if (!"file".equalsIgnoreCase(inputFile.uri().getScheme())) {
      return;
    }
    String configFile = null; // TODO
    CssAnalyzerBridgeServer.Issue[] issues = cssAnalyzerBridgeServer.analyze(new AnalysisRequest(new File(inputFile.uri()).getAbsolutePath(), configFile));
    saveIssues(context, inputFile, issues);
  }

  private void saveIssues(SensorContext context, InputFile inputFile, CssAnalyzerBridgeServer.Issue[] issues) {
    for (CssAnalyzerBridgeServer.Issue issue : issues) {
      NewIssue sonarIssue = context.newIssue();

      RuleKey ruleKey = cssRules.getActiveSonarKey(issue.rule);

      if (ruleKey == null) {
        if ("CssSyntaxError".equals(issue.rule)) {
          String errorMessage = issue.text.replace("(CssSyntaxError)", "").trim();
          LOG.error("Failed to parse {}, line {}, {}", inputFile.uri(), issue.line, errorMessage);
        } else {
          LOG.error("Unknown stylelint rule or rule not enabled: '" + issue.rule + "'");
        }

      } else {
        NewIssueLocation location = sonarIssue.newLocation()
          .on(inputFile)
          .at(inputFile.selectLine(issue.line))
          .message(normalizeMessage(issue.text));

        sonarIssue
          .at(location)
          .forRule(ruleKey)
          .save();
      }
    }
  }

  private static List<InputFile> getInputFiles(SensorContext context) {
    FileSystem fileSystem = context.fileSystem();
    FilePredicates predicates = context.fileSystem().predicates();
    FilePredicate mainFilePredicate = predicates.and(
      fileSystem.predicates().hasType(InputFile.Type.MAIN),
      fileSystem.predicates().hasLanguages(CssLanguage.KEY, "php", "html", "js"));
    return StreamSupport.stream(fileSystem.inputFiles(mainFilePredicate).spliterator(), false)
      .collect(Collectors.toList());
  }

  private boolean isSuccessful(int exitValue) {
    // exit codes 0 and 2 are expected. 0 - means no issues were found, 2 - means that at least one "error-level" rule found issue
    // see https://github.com/stylelint/stylelint/blob/master/docs/user-guide/cli.md#exit-codes
    boolean isSuccessful = exitValue == 0 || exitValue == 2;
    if (!isSuccessful) {
      LOG.error("Analysis didn't terminate normally, please verify ERROR and WARN logs above. Exit code {}", exitValue);
    }
    return isSuccessful;
  }

  private void createLinterConfig(File deployDestination) throws IOException {
    String configPath = linterCommandProvider.configPath(deployDestination);
    StylelintConfig config = cssRules.getConfig();
    final GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(StylelintConfig.class, config);
    final Gson gson = gsonBuilder.create();
    String configAsJson = gson.toJson(config);
    Files.write(Paths.get(configPath), Collections.singletonList(configAsJson), StandardCharsets.UTF_8);
  }

  private static String normalizeMessage(String message) {
    // stylelint messages have format "message (rulekey)"
    Pattern pattern = Pattern.compile("(.+)\\([a-z\\-]+\\)");
    Matcher matcher = pattern.matcher(message);
    if (matcher.matches()) {
      return matcher.group(1);
    } else {
      return message;
    }
  }

  private void saveIssues(SensorContext context, String issuesAsJson) {
    IssuesPerFile[] issues;
    try {
      issues = new Gson().fromJson(issuesAsJson, IssuesPerFile[].class);
    } catch (JsonSyntaxException e) {
      throw new IllegalStateException("Failed to parse JSON result of external linting process execution: \n-------\n" + issuesAsJson + "\n-------", e);
    }

    FileSystem fileSystem = context.fileSystem();

    for (IssuesPerFile issuesPerFile : issues) {
      String filePath = issuesPerFile.source;
      InputFile inputFile = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(filePath));

      if (inputFile == null) {
        LOG.debug("Following file is not part of the project: [{}]. {} found issue(s) on it will not be reported.", filePath, issuesPerFile.warnings.length);
        continue;
      }

      for (Issue issue : issuesPerFile.warnings) {
        saveIssue(context, inputFile, issue);
      }
    }
  }

  private void saveIssue(SensorContext context, InputFile inputFile, Issue issue) {
    NewIssue sonarIssue = context.newIssue();

    RuleKey ruleKey = cssRules.getActiveSonarKey(issue.rule);

    if (ruleKey == null) {
      if ("CssSyntaxError".equals(issue.rule)) {
        String errorMessage = issue.text.replace("(CssSyntaxError)", "").trim();
        LOG.error("Failed to parse {}, line {}, {}", inputFile.uri(), issue.line, errorMessage);
      } else {
        LOG.error("Unknown stylelint rule or rule not enabled: '" + issue.rule + "'");
      }

    } else {
      NewIssueLocation location = sonarIssue.newLocation()
        .on(inputFile)
        .at(inputFile.selectLine(issue.line))
        .message(normalizeMessage(issue.text));

      sonarIssue
        .at(location)
        .forRule(ruleKey)
        .save();
    }
  }

}
