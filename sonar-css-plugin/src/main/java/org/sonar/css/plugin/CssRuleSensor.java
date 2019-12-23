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
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.sonar.api.SonarProduct;
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
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.css.plugin.CssRules.StylelintConfig;
import org.sonar.css.plugin.server.AnalyzerBridgeServer.Issue;
import org.sonar.css.plugin.server.AnalyzerBridgeServer.Request;
import org.sonar.css.plugin.server.CssAnalyzerBridgeServer;
import org.sonar.css.plugin.server.exception.ServerAlreadyFailedException;
import org.sonarsource.analyzer.commons.ProgressReport;
import org.sonarsource.nodejs.NodeCommandException;

public class CssRuleSensor implements Sensor {

  private static final Logger LOG = Loggers.get(CssRuleSensor.class);
  private static final String CONFIG_PATH = "css-bundle/stylelintconfig.json";

  private final CssRules cssRules;
  private final CssAnalyzerBridgeServer cssAnalyzerBridgeServer;
  private final AnalysisWarnings analysisWarnings;


  public CssRuleSensor(
    CheckFactory checkFactory,
    CssAnalyzerBridgeServer cssAnalyzerBridgeServer,
    @Nullable AnalysisWarnings analysisWarnings
  ) {
    this.cssRules = new CssRules(checkFactory);
    this.cssAnalyzerBridgeServer = cssAnalyzerBridgeServer;
    this.analysisWarnings = analysisWarnings;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .createIssuesForRuleRepository("css")
      .name("SonarCSS Rules");
  }

  @Override
  public void execute(SensorContext context) {
    reportOldNodeProperty(context);

    boolean failFast = context.config().getBoolean("sonar.internal.analysis.failFast").orElse(false);

    try {
      List<InputFile> inputFiles = getInputFiles(context);
      if (inputFiles.isEmpty()) {
        LOG.info("No CSS, PHP or HTML files are found in the project. CSS analysis is skipped.");
      } else {
        cssAnalyzerBridgeServer.startServerLazily(context);
        File configFile = createLinterConfig(context);
        analyzeFiles(context, inputFiles, configFile);
      }
    } catch (CancellationException e) {
      // do not propagate the exception
      LOG.info(e.toString());
    } catch (ServerAlreadyFailedException e) {
      LOG.debug("Skipping start of css-bundle server due to the failure during first analysis");
      LOG.debug("Skipping execution of CSS rules due to the problems with css-bundle server");
    } catch (NodeCommandException e) {
      LOG.error(e.getMessage(), e);
      reportAnalysisWarning("CSS rules were not executed. " + e.getMessage());
      if (failFast) {
        throw new IllegalStateException("Analysis failed (\"sonar.internal.analysis.failFast\"=true)", e);
      }
    } catch (Exception e) {
      LOG.error("Failure during analysis, " + cssAnalyzerBridgeServer.getCommandInfo(), e);
      if (failFast) {
        throw new IllegalStateException("Analysis failed (\"sonar.internal.analysis.failFast\"=true)", e);
      }
    }
  }

  private void reportOldNodeProperty(SensorContext context) {
    if (context.config().hasKey(CssPlugin.FORMER_NODE_EXECUTABLE)) {
      String msg = "Property '" + CssPlugin.FORMER_NODE_EXECUTABLE + "' is ignored, 'sonar.nodejs.executable' should be used instead";
      LOG.warn(msg);
      reportAnalysisWarning(msg);
    }
  }

  void analyzeFiles(SensorContext context, List<InputFile> inputFiles, File configFile) throws InterruptedException, IOException {
    ProgressReport progressReport = new ProgressReport("Analysis progress", TimeUnit.SECONDS.toMillis(10));
    boolean success = false;
    try {
      progressReport.start(inputFiles.stream().map(InputFile::toString).collect(Collectors.toList()));
      for (InputFile inputFile : inputFiles) {
        if (context.isCancelled()) {
          throw new CancellationException("Analysis interrupted because the SensorContext is in cancelled state");
        }
        if (cssAnalyzerBridgeServer.isAlive()) {
          try {
            analyzeFile(context, inputFile, configFile);
          } catch (IOException | RuntimeException e) {
            throw new IOException("Failure during analysis of " + inputFile.uri() + ": " + e.getMessage(), e);
          }
          progressReport.nextFile();
        } else {
          throw new IllegalStateException("css-bundle server is not answering");
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

  void analyzeFile(SensorContext context, InputFile inputFile, File configFile) throws IOException {
    URI uri = inputFile.uri();
    if (!"file".equalsIgnoreCase(uri.getScheme())) {
      LOG.debug("Skipping {} as it has not 'file' scheme", uri);
      return;
    }
    String fileContent = shouldSendFileContent(context, inputFile) ? inputFile.contents() : null;
    Request request = new Request(new File(uri).getAbsolutePath(), fileContent, configFile.toString());
    LOG.debug("Analyzing " + request.filePath);
    Issue[] issues = cssAnalyzerBridgeServer.analyze(request);
    LOG.debug("Found {} issue(s)", issues.length);
    saveIssues(context, inputFile, issues);
  }

  private static boolean shouldSendFileContent(SensorContext context, InputFile file) {
    return context.runtime().getProduct() == SonarProduct.SONARLINT
      || !StandardCharsets.UTF_8.equals(file.charset());
  }

  private void saveIssues(SensorContext context, InputFile inputFile, Issue[] issues) {
    for (Issue issue : issues) {
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
      fileSystem.predicates().hasLanguages(CssLanguage.KEY, "php", "web"));
    return StreamSupport.stream(fileSystem.inputFiles(mainFilePredicate).spliterator(), false)
      .collect(Collectors.toList());
  }

  private File createLinterConfig(SensorContext context) throws IOException {
    StylelintConfig config = cssRules.getConfig();
    final GsonBuilder gsonBuilder = new GsonBuilder();
    gsonBuilder.registerTypeAdapter(StylelintConfig.class, config);
    final Gson gson = gsonBuilder.create();
    String configAsJson = gson.toJson(config);
    File configFile = new File(context.fileSystem().workDir(), CONFIG_PATH).getAbsoluteFile();
    Files.createDirectories(configFile.toPath().getParent());
    Files.write(configFile.toPath(), Collections.singletonList(configAsJson), StandardCharsets.UTF_8);
    return configFile;
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

  private void reportAnalysisWarning(String message) {
    if (analysisWarnings != null) {
      analysisWarnings.addUnique(message);
    }
  }
}
