/*
 * SonarCSS
 * Copyright (C) 2018-2018 SonarSource SA
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
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

public class CssRuleSensor implements Sensor {

  private static final Logger LOG = Loggers.get(CssRuleSensor.class);
  private static final int MIN_NODE_VERSION = 6;
  private static final String WARNING_PREFIX = "CSS files were not analyzed. ";

  private final BundleHandler bundleHandler;
  private final CssRules cssRules;
  private final LinterCommandProvider linterCommandProvider;
  @Nullable
  private final AnalysisWarningsWrapper analysisWarnings;
  private final ExternalProcessStreamConsumer externalProcessStreamConsumer = new ExternalProcessStreamConsumer();

  public CssRuleSensor(BundleHandler bundleHandler,
                       CheckFactory checkFactory,
                       LinterCommandProvider linterCommandProvider,
                       @Nullable AnalysisWarningsWrapper analysisWarnings) {
    this.bundleHandler = bundleHandler;
    this.linterCommandProvider = linterCommandProvider;
    this.cssRules = new CssRules(checkFactory);
    this.analysisWarnings = analysisWarnings;
  }

  public CssRuleSensor(BundleHandler bundleHandler,
                       CheckFactory checkFactory,
                       LinterCommandProvider linterCommandProvider) {
    this(bundleHandler, checkFactory, linterCommandProvider, null);
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage(CssLanguage.KEY)
      .name("SonarCSS Rules");
  }

  @Override
  public void execute(SensorContext context) {
    if (cssRules.isEmpty()) {
      LOG.warn("No rules are activated in CSS Quality Profile");
      return;
    }

    if (!checkCompatibleNodeVersion(context)) {
      return;
    }

    File deployDestination = context.fileSystem().workDir();
    bundleHandler.deployBundle(deployDestination);
    String[] commandParts = linterCommandProvider.commandParts(deployDestination, context);

    try {
      ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
      createLinterConfig(deployDestination);
      Process process = processBuilder.start();
      StringBuilder output = new StringBuilder();
      externalProcessStreamConsumer.consumeStream(process.getInputStream(), output::append);
      externalProcessStreamConsumer.consumeStream(process.getErrorStream(), LOG::error);
      if (isSuccessful(process)) {
        saveIssues(context, output.toString());
      }
    } catch (Exception e) {
      LOG.error("Failed to run external linting process " + String.join(" ", commandParts), e);
    } finally {
      externalProcessStreamConsumer.shutdownNow();
    }
  }

  private boolean isSuccessful(Process process) throws InterruptedException {
    int exitValue = process.waitFor();
    externalProcessStreamConsumer.await();
    // exit codes 0 and 2 are expected. 0 - means no issues were found, 2 - means that at least one "error-level" rule found issue
    // see https://github.com/stylelint/stylelint/blob/master/docs/user-guide/cli.md#exit-codes
    boolean isSuccessful = exitValue == 0 || exitValue == 2;
    if (!isSuccessful) {
      LOG.error("Analysis didn't terminate normally, please verify ERROR and WARN logs above. Exit code {}", exitValue);
    }
    return isSuccessful;
  }

  private boolean checkCompatibleNodeVersion(SensorContext context) {
    String nodeExecutable = linterCommandProvider.nodeExecutable(context.config());
    LOG.debug("Checking node version");
    String messageSuffix = "No CSS files will be analyzed.";

    String version;
    try {
      Process process = Runtime.getRuntime().exec(nodeExecutable + " -v");
      version = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim();
    } catch (Exception e) {
      LOG.error("Failed to get Node.js version. " + messageSuffix, e);
      if (analysisWarnings != null) {
        analysisWarnings.addUnique(WARNING_PREFIX + "Node.js version could not be detected using command: " + nodeExecutable + " -v");
      }
      return false;
    }

    Pattern versionPattern = Pattern.compile("v?(\\d+)\\.\\d+\\.\\d+");
    Matcher versionMatcher = versionPattern.matcher(version);
    if (versionMatcher.matches()) {
      int major = Integer.parseInt(versionMatcher.group(1));
      if (major < MIN_NODE_VERSION) {
        String message = String.format("Only Node.js v%s or later is supported, got %s.", MIN_NODE_VERSION, version);
        LOG.error(message + ' ' + messageSuffix);
        if (analysisWarnings != null) {
          analysisWarnings.addUnique(WARNING_PREFIX + message);
        }
        return false;
      }
    } else {
      String message = String.format("Failed to parse Node.js version, got '%s'.", version);
      LOG.error(message + ' ' + messageSuffix);
      if (analysisWarnings != null) {
        analysisWarnings.addUnique(WARNING_PREFIX + message);
      }
      return false;
    }

    LOG.debug(String.format("Using Node.js %s", version));
    return true;
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
      InputFile inputFile = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(issuesPerFile.source));

      if (inputFile != null) {
        for (Issue issue : issuesPerFile.warnings) {
          saveIssue(context, inputFile, issue);
        }
      }
    }
  }

  private void saveIssue(SensorContext context, InputFile inputFile, Issue issue) {
    NewIssue sonarIssue = context.newIssue();

    RuleKey ruleKey = cssRules.getActiveSonarKey(issue.rule);

    if (ruleKey == null) {
      if ("CssSyntaxError".equals(issue.rule)) {
        LOG.error("Failed to parse " + inputFile.uri());
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
