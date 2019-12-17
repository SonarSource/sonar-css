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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Ignore;
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
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.css.plugin.server.CssAnalyzerBridgeServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
  private DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
  private AnalysisWarningsWrapper analysisWarnings = mock(AnalysisWarningsWrapper.class);
  private CssAnalyzerBridgeServer cssAnalyzerBridgeServer;

  @Before
  public void setUp() {
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
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

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "testconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_old_property_is_provided() {
    CssRuleSensor sensor = createCssRuleSensor(analysisWarnings);
    context.settings().setProperty(CssPlugin.FORMER_NODE_EXECUTABLE, "foo");
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Property 'sonar.css.node' is ignored, 'sonar.nodejs.executable' should be used instead");
    verify(analysisWarnings).addUnique(eq("Property 'sonar.css.node' is ignored, 'sonar.nodejs.executable' should be used instead"));

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void test_invalid_node() {
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Some problem happened. No CSS files will be analyzed.");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_execute_with_analysisWarnings() throws IOException {
    CssRuleSensor sensor = createCssRuleSensor(analysisWarnings);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo("some message");

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "testconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_invalid_node_command_with_analysisWarnings() {
    CssRuleSensor sensor = createCssRuleSensor(analysisWarnings);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Some problem happened. No CSS files will be analyzed.");
    verify(analysisWarnings).addUnique(eq("CSS files were not analyzed. Some problem happened."));
  }

  @Test
  public void test_error() {
    // to do /executables/mockError.js inputFile.absolutePath()
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.ERROR)).anyMatch(s -> s.startsWith("Failed to run external linting process"));
  }

  @Test
  public void test_not_execute_rules_if_nothing_enabled() {
    // TODO /executables/mockError.js inputFile.absolutePath()
    CssRuleSensor sensor = new CssRuleSensor(new CheckFactory(new TestActiveRules()), cssAnalyzerBridgeServer, analysisWarnings);
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No rules are activated in CSS Quality Profile");
  }

  @Test
  @Ignore
  public void test_stylelint_throws() {
    // TODO /executables/mockThrow.js inputFile.absolutePath()
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    await().until(() -> logTester.logs(LoggerLevel.ERROR)
      .contains("throw new Error('houps!');"));
  }

  @Test
  @Ignore
  public void test_stylelint_exitvalue() {
    // TODO "/executables/mockExit.js", "1"
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    await().until(() -> logTester.logs(LoggerLevel.ERROR)
      .contains("Analysis didn't terminate normally, please verify ERROR and WARN logs above. Exit code 1"));
  }

  @Test
  public void test_syntax_error() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    // TODO /executables/mockSyntaxError.js inputFile.absolutePath()
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to parse " + inputFile.uri() + ", line 2, Missed semicolon");
  }

  @Test
  public void test_unknown_rule() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    // TODO /executables/mockUnknownRule.js inputFile.absolutePath()
    CssRuleSensor sensor = createCssRuleSensor();
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Unknown stylelint rule or rule not enabled: 'unknown-rule-key'");
  }

  @Test
  public void name() {
    URI uri = Paths.get("/tmp/f1.txt").toUri();
    new File(uri);
    String scheme = uri.getScheme();
    if ((scheme == null) || !scheme.equalsIgnoreCase("file"))

    System.out.println(uri.getScheme());
    System.out.println(uri.getPath());

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
    return new CssRuleSensor(checkFactory, cssAnalyzerBridgeServer);
  }

  private CssRuleSensor createCssRuleSensor(@Nullable AnalysisWarningsWrapper analysisWarnings) {
    return new CssRuleSensor(checkFactory, cssAnalyzerBridgeServer, analysisWarnings);
  }

}
