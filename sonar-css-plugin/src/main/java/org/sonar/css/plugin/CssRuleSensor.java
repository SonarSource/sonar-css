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
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private final BundleHandler bundleHandler;
  private final CssRules cssRules;
  private final LinterCommandProvider linterCommandProvider;

  public CssRuleSensor(BundleHandler bundleHandler, CheckFactory checkFactory, LinterCommandProvider linterCommandProvider) {
    this.bundleHandler = bundleHandler;
    this.linterCommandProvider = linterCommandProvider;
    this.cssRules = new CssRules(checkFactory);
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
    ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
    String command = String.join(" ", commandParts);

    try {
      createConfig(deployDestination);
      Process process = processBuilder.start();

      try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        IssuesPerFile[] issues = new Gson().fromJson(inputStreamReader, IssuesPerFile[].class);
        saveIssues(context, issues);
      }

    } catch (IOException e) {
      throw new IllegalStateException(String.format("Failed to run external process '%s'", command), e);

    } catch (JsonSyntaxException e) {
      throw new IllegalStateException(String.format("Failed to parse json result of external process execution '%s'. To diagnose, try to run it manually.", command), e);
    }
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
      return false;
    }

    Pattern versionPattern = Pattern.compile("v?(\\d+)\\.\\d+\\.\\d+");
    Matcher versionMatcher = versionPattern.matcher(version);
    if (versionMatcher.matches()) {
      int major = Integer.parseInt(versionMatcher.group(1));
      if (major < MIN_NODE_VERSION) {
        String message = String.format("Only Node.js v%s or later is supported, got %s. %s", MIN_NODE_VERSION, version, messageSuffix);
        LOG.error(message);
        return false;
      }
    } else {
      String message = String.format("Failed to parse Node.js version, got '%s'. %s", version, messageSuffix);
      LOG.error(message);
      return false;
    }

    LOG.debug(String.format("Using Node.js %s", version));
    return true;
  }

  private void createConfig(File deployDestination) throws IOException {
    String configPath = linterCommandProvider.configPath(deployDestination);
    StylelintConfig config = cssRules.getConfig();
    String configAsJson = new Gson().toJson(config);
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

  private void saveIssues(SensorContext context, IssuesPerFile[] issues) {
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
