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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.css.plugin.server.CssAnalyzerBridgeServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.sonar.css.plugin.server.CssAnalyzerBridgeServerTest.createCssAnalyzerBridgeServer;

public class CssRuleSensorTest {

  @Rule
  public final LogTester logTester = new LogTester();

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  @Rule
  public ExpectedException thrown= ExpectedException.none();

  private static CheckFactory checkFactory = new CheckFactory(new TestActiveRules("S4647", "S4656"));

  private static final File BASE_DIR = new File("src/test/resources").getAbsoluteFile();

  private SensorContextTester context = SensorContextTester.create(BASE_DIR);
  private AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
  private CssAnalyzerBridgeServer cssAnalyzerBridgeServer;

  @Before
  public void setUp() {
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    Awaitility.setDefaultTimeout(5, TimeUnit.MINUTES);
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer("startServer.js");
  }

  @Test
  public void test_descriptor() {
    CssRuleSensor sensor = new CssRuleSensor(checkFactory, cssAnalyzerBridgeServer, analysisWarnings);
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("SonarCSS Rules");
    assertThat(sensorDescriptor.languages()).isEmpty();
  }

  @Test
  public void test_execute() throws IOException {
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo("some message");

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "css-bundle/stylelintconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_old_property_is_provided() {
    CssRuleSensor sensor = createCssRuleSensor();
    context.settings().setProperty(CssPlugin.FORMER_NODE_EXECUTABLE, "foo");
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Property 'sonar.css.node' is ignored, 'sonar.nodejs.executable' should be used instead");
    verify(analysisWarnings).addUnique(eq("Property 'sonar.css.node' is ignored, 'sonar.nodejs.executable' should be used instead"));

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void test_execute_with_analysisWarnings() throws IOException {
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo("some message");

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "css-bundle/stylelintconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_not_execute_rules_if_nothing_enabled() {
    CssRuleSensor sensor = new CssRuleSensor(new CheckFactory(new TestActiveRules()), cssAnalyzerBridgeServer, analysisWarnings);
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No rules are activated in CSS Quality Profile");
  }

  @Test
  public void test_syntax_error() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "syntax-error.css");
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);
    assertThat(context.allIssues()).isEmpty();
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to parse " + inputFile.uri() + ", line 2, Missed semicolon");
  }

  @Test
  public void test_unknown_rule() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    createInputFile(context, "some css content\n on 2 lines", "unknown-rule.css");
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).isEmpty();
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Unknown stylelint rule or rule not enabled: 'unknown-rule-key'");
  }

  private static DefaultInputFile createInputFile(SensorContextTester sensorContext, String content, String relativePath) {
    DefaultInputFile inputFile = new TestInputFileBuilder("moduleKey", relativePath)
      .setModuleBaseDir(sensorContext.fileSystem().baseDirPath())
      .setType(Type.MAIN)
      .setLanguage(CssLanguage.KEY)
      .setCharset(StandardCharsets.UTF_8)
      .setContents(content)
      .build();

    sensorContext.fileSystem().add(inputFile);
    return inputFile;
  }

  private CssRuleSensor createCssRuleSensor() {
    return new CssRuleSensor(checkFactory, cssAnalyzerBridgeServer, analysisWarnings);
  }

}
