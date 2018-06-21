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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Severity;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.issue.NewExternalIssue;
import org.sonar.api.batch.sensor.issue.NewIssueLocation;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.Context;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.css.plugin.StylelintReport.Issue;
import org.sonar.css.plugin.StylelintReport.IssuesPerFile;

public class StylelintReportSensor implements Sensor {

  private static final Logger LOG = Loggers.get(StylelintReportSensor.class);

  private static final String REPOSITORY = "stylelint";

  private static final long DEFAULT_REMEDIATION_COST = 5L;
  private static final Severity DEFAULT_SEVERITY = Severity.MAJOR;
  private static final String FILE_EXCEPTION_MESSAGE = "No issues information will be saved as the report file can't be read.";

  private static final Set<String> BUG_RULES = new HashSet<>(Arrays.asList(
    "selector-type-no-unknown",
    "no-invalid-double-slash-comments",
    "no-descending-specificity",
    "at-rule-no-unknown",
    "selector-type-no-unknown",
    "selector-pseudo-element-no-unknown",
    "selector-pseudo-class-no-unknown",
    "declaration-block-no-shorthand-property-overrides",
    "declaration-block-no-duplicate-properties",
    "keyframe-declaration-no-important",
    "property-no-unknown",
    "unit-no-unknown",
    "function-linear-gradient-no-nonstandard-direction",
    "function-calc-no-unspaced-operator",
    "font-family-no-missing-generic-family-keyword",
    "color-no-invalid-hex"
  ));

  private final CssRules cssRules;

  public StylelintReportSensor(CheckFactory checkFactory) {
    cssRules = new CssRules(checkFactory);
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage(CssLanguage.KEY)
      .name("Import of stylelint issues");
  }

  @Override
  public void execute(SensorContext context) {
    boolean externalIssuesSupported = context.getSonarQubeVersion().isGreaterThanOrEqual(Version.create(7, 2));
    String[] reportPaths = context.config().getStringArray(CssPlugin.STYLELINT_REPORT_PATHS);

    if (reportPaths.length == 0) {
      return;
    }

    if (!externalIssuesSupported) {
      LOG.error("Import of external issues requires SonarQube 7.2 or greater.");
      return;
    }

    for (String reportPath : reportPaths) {
      File report = getIOFile(context.fileSystem().baseDir(), reportPath);
      importReport(report, context);
    }
  }

  private void importReport(File report, SensorContext context) {
    LOG.info("Importing {}", report.getAbsoluteFile());

    try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(report), StandardCharsets.UTF_8)) {
      IssuesPerFile[] issues = new Gson().fromJson(inputStreamReader, IssuesPerFile[].class);
      for (IssuesPerFile issuesPerFile : issues) {
        InputFile inputFile = getInputFile(context, issuesPerFile.source);
        if (inputFile != null) {
          for (Issue issue : issuesPerFile.warnings) {
            saveStylelintIssue(context, issue, inputFile);
          }
        }
      }
    } catch (IOException e) {
      LOG.error(FILE_EXCEPTION_MESSAGE, e);
    } catch (JsonSyntaxException e) {
      LOG.error("Failed to parse json stylelint report", e);
    }
  }

  @Nullable
  private static InputFile getInputFile(SensorContext context, String fileName) {
    FilePredicates predicates = context.fileSystem().predicates();
    InputFile inputFile = context.fileSystem().inputFile(predicates.or(predicates.hasRelativePath(fileName), predicates.hasAbsolutePath(fileName)));
    if (inputFile == null) {
      LOG.warn("No input file found for {}. No stylelint issues will be imported on this file.", fileName);
      return null;
    }
    return inputFile;
  }

  private void saveStylelintIssue(SensorContext context, Issue issue, InputFile inputFile) {
    String stylelintKey = issue.rule;

    RuleKey sonarKey = cssRules.getActiveSonarKey(stylelintKey);
    if (sonarKey != null) {
      String message = "Stylelint issue for rule '{}' is skipped because this rule is activated in your SonarQube profile for CSS (rule key in SQ {})";
      LOG.debug(message, stylelintKey, sonarKey.toString());
      return;
    }

    NewExternalIssue newExternalIssue = context.newExternalIssue();

    NewIssueLocation primaryLocation = newExternalIssue.newLocation()
      .message(issue.text)
      .on(inputFile)
      .at(inputFile.selectLine(issue.line));

    newExternalIssue
      .at(primaryLocation)
      .forRule(RuleKey.of(REPOSITORY, stylelintKey))
      .type(ruleType(stylelintKey))
      .severity(DEFAULT_SEVERITY)
      .remediationEffortMinutes(DEFAULT_REMEDIATION_COST)
      .save();
  }

  private static RuleType ruleType(String stylelintKey) {
    return BUG_RULES.contains(stylelintKey)
      ? RuleType.BUG
      : RuleType.CODE_SMELL;
  }

  /**
   * Returns a java.io.File for the given path.
   * If path is not absolute, returns a File with module base directory as parent path.
   */
  private static File getIOFile(File baseDir, String path) {
    File file = new File(path);
    if (!file.isAbsolute()) {
      file = new File(baseDir, path);
    }

    return file;
  }

  static void createExternalRuleRepository(Context context) {
    NewRepository externalRepo = context.createExternalRepository(REPOSITORY, CssLanguage.KEY).setName(REPOSITORY);
    String pathToRulesMeta = "org/sonar/l10n/css/rules/" + REPOSITORY + "/rules.json";
    String description = "See the description of %s rule <code>%s</code> at <a href=\"%s\">%s website</a>.";

    try (InputStreamReader inputStreamReader = new InputStreamReader(StylelintReportSensor.class.getClassLoader().getResourceAsStream(pathToRulesMeta), StandardCharsets.UTF_8)) {
      ExternalRule[] rules = new Gson().fromJson(inputStreamReader, ExternalRule[].class);
      for (ExternalRule rule : rules) {
        NewRule newRule = externalRepo.createRule(rule.key).setName(rule.name);
        newRule.setHtmlDescription(String.format(description, REPOSITORY, rule.key, rule.url, REPOSITORY));
        newRule.setDebtRemediationFunction(newRule.debtRemediationFunctions().constantPerIssue(DEFAULT_REMEDIATION_COST + "min"));
        if (BUG_RULES.contains(rule.key)) {
          newRule.setType(RuleType.BUG);
        }
      }

    } catch (IOException e) {
      throw new IllegalStateException("Can't read resource: " + pathToRulesMeta, e);
    }

    externalRepo.done();
  }

  private static class ExternalRule {
    String url;
    String key;
    String name;
  }
}
