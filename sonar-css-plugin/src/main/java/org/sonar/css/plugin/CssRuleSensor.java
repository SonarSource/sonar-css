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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class CssRuleSensor implements Sensor {

  private static final Logger LOG = Loggers.get(CssRuleSensor.class);

  private final BundleHandler bundleHandler;
  private final CssRules cssRules;
  private final RulesExecution rulesExecution;

  public CssRuleSensor(BundleHandler bundleHandler, CheckFactory checkFactory, RulesExecution rulesExecution) {
    this.bundleHandler = bundleHandler;
    this.rulesExecution = rulesExecution;
    this.cssRules = new CssRules(checkFactory);
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage(CssLanguage.KEY)
      .name("SonarCSS Rules")
      .onlyOnFileType(InputFile.Type.MAIN);
  }

  @Override
  public void execute(SensorContext context) {
    File deployDestination = context.fileSystem().workDir();
    bundleHandler.deployBundle(deployDestination);

    File projectBaseDir = context.fileSystem().baseDir();

    String[] commandElements = rulesExecution.commandElements(deployDestination, projectBaseDir);
    ProcessBuilder processBuilder = new ProcessBuilder(commandElements);

    try {
      Process process = processBuilder.start();

      try (InputStreamReader inputStreamReader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
        IssuesPerFile[] issues = new Gson().fromJson(inputStreamReader, IssuesPerFile[].class);
        saveIssues(context, cssRules, issues);
      }

    } catch (IOException e) {
      String command = String.join(" ", commandElements);
      throw new IllegalStateException(String.format("Failed to run external process `%s`. Rerun analysis with -X for more information", command), e);
    }
  }

  private void saveIssues(SensorContext context, CssRules cssRules, IssuesPerFile[] issues) {
    FileSystem fileSystem = context.fileSystem();

    for (IssuesPerFile issuesPerFile : issues) {
      InputFile inputFile = fileSystem.inputFile(fileSystem.predicates().hasAbsolutePath(issuesPerFile.source));

      if (inputFile != null) {
        for (Issue issue : issuesPerFile.warnings) {
          NewIssue sonarIssue = context.newIssue();
          NewIssueLocation location = sonarIssue.newLocation().on(inputFile).at(inputFile.selectLine(issue.line));
          location.message(issue.text);
          sonarIssue.at(location);
          sonarIssue.forRule(cssRules.getSonarKey(issue.rule));
          sonarIssue.save();
        }
      }
    }
  }

  static class IssuesPerFile {
    String source;
    Issue[] warnings;
  }

  static class Issue {
    int line;
    String rule;
    String text;
  }

}
