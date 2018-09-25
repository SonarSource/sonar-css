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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
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
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.batch.sensor.issue.Issue;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.css.plugin.bundle.BundleHandler;
import org.sonar.css.plugin.bundle.CssBundleHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

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

  @Before
  public void setUp() {
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    Awaitility.setDefaultTimeout(5, TimeUnit.MINUTES);
  }

  @Test
  public void test_descriptor() {
    CssRuleSensor sensor = new CssRuleSensor(new CssBundleHandler(), checkFactory, new StylelintCommandProvider(), analysisWarnings);
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("SonarCSS Rules");
    assertThat(sensorDescriptor.languages()).containsOnly("css");
  }

  @Test
  public void test_execute() throws IOException {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo("some message");

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "testconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_invalid_node() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable += " " + TestLinterCommandProvider.resourceScript("/executables/invalidNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("CSS files were not analyzed. Failed to parse Node.js version, got 'Invalid version'.");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_no_node() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable = TestLinterCommandProvider.resourceScript("/executables/invalidNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to get Node.js version. No CSS files will be analyzed.");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_old_node() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable += " " + TestLinterCommandProvider.resourceScript("/executables/oldNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("CSS files were not analyzed. Only Node.js v6 or later is supported, got 3.2.1.");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_execute_with_analysisWarnings() throws IOException {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    CssRuleSensor sensor = createCssRuleSensor(commandProvider, analysisWarnings);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
    Issue issue = context.allIssues().iterator().next();
    assertThat(issue.primaryLocation().message()).isEqualTo("some message");

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "testconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true,\"declaration-block-no-duplicate-properties\":[true,{\"ignore\":[\"consecutive-duplicates-with-different-values\"]}]}}");
    verifyZeroInteractions(analysisWarnings);
  }

  @Test
  public void test_invalid_node_with_analysisWarnings() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable += " " + TestLinterCommandProvider.resourceScript("/executables/invalidNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider, analysisWarnings);
    sensor.execute(context);

    String message = "CSS files were not analyzed. Failed to parse Node.js version, got 'Invalid version'.";
    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains(message);
    verify(analysisWarnings).addUnique(eq(message));
  }

  @Test
  public void test_no_node_with_analysisWarnings() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable = TestLinterCommandProvider.resourceScript("/executables/invalidNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider, analysisWarnings);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to get Node.js version. No CSS files will be analyzed.");
    verify(analysisWarnings).addUnique(matches("CSS files were not analyzed. Node.js version could not be detected using command: .* -v"));
  }

  @Test
  public void test_old_node_with_analysisWarnings() {
    TestLinterCommandProvider commandProvider = getCommandProvider();
    commandProvider.nodeExecutable += " " + TestLinterCommandProvider.resourceScript("/executables/oldNodeVersion.js");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider, analysisWarnings);
    sensor.execute(context);

    String message = "CSS files were not analyzed. Only Node.js v6 or later is supported, got 3.2.1.";

    assertThat(context.allIssues()).hasSize(0);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains(message);
    verify(analysisWarnings).addUnique(eq(message));
  }

  @Test
  public void test_error() {
    TestLinterCommandProvider commandProvider = new TestLinterCommandProvider().nodeScript("/executables/mockError.js", inputFile.absolutePath());
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.ERROR)).anyMatch(s -> s.startsWith("Failed to run external linting process"));
  }

  @Test
  public void test_not_execute_rules_if_nothing_enabled() {
    TestLinterCommandProvider commandProvider = new TestLinterCommandProvider().nodeScript("/executables/mockError.js", inputFile.absolutePath());
    CssRuleSensor sensor = new CssRuleSensor(new TestBundleHandler(), new CheckFactory(new TestActiveRules()), commandProvider, analysisWarnings);
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No rules are activated in CSS Quality Profile");
  }

  @Test
  public void test_stylelint_throws() {
    TestLinterCommandProvider commandProvider = new TestLinterCommandProvider().nodeScript("/executables/mockThrow.js", inputFile.absolutePath());
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    await().until(() -> logTester.logs(LoggerLevel.ERROR)
      .contains("throw new Error('houps!');"));
  }

  @Test
  public void test_stylelint_exitvalue() {
    TestLinterCommandProvider commandProvider = new TestLinterCommandProvider().nodeScript("/executables/mockExit.js", "1");
    CssRuleSensor sensor = createCssRuleSensor(commandProvider);
    sensor.execute(context);

    await().until(() -> logTester.logs(LoggerLevel.ERROR)
      .contains("Analysis didn't terminate normally, please verify ERROR and WARN logs above. Exit code 1"));
  }

  @Test
  public void test_syntax_error() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    TestLinterCommandProvider rulesExecution = new TestLinterCommandProvider().nodeScript("/executables/mockSyntaxError.js", inputFile.absolutePath());
    CssRuleSensor sensor = createCssRuleSensor(rulesExecution);
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("Failed to parse " + inputFile.uri());
  }

  @Test
  public void test_unknown_rule() {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    TestLinterCommandProvider rulesExecution = new TestLinterCommandProvider().nodeScript("/executables/mockUnknownRule.js", inputFile.absolutePath());
    CssRuleSensor sensor = createCssRuleSensor(rulesExecution);
    sensor.execute(context);

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

  private CssRuleSensor createCssRuleSensor(TestLinterCommandProvider commandProvider) {
    return new CssRuleSensor(new TestBundleHandler(), checkFactory, commandProvider);
  }

  private CssRuleSensor createCssRuleSensor(TestLinterCommandProvider commandProvider, @Nullable AnalysisWarningsWrapper analysisWarnings) {
    return new CssRuleSensor(new TestBundleHandler(), checkFactory, commandProvider, analysisWarnings);
  }

  private TestLinterCommandProvider getCommandProvider() {
    return new TestLinterCommandProvider().nodeScript("/executables/mockStylelint.js", inputFile.absolutePath());
  }

  private static class TestLinterCommandProvider implements LinterCommandProvider {

    String nodeExecutable = findNodeExecutable();

    private String[] elements;

    private static String findNodeExecutable() {
      try {
        String nodeFromMavenPlugin = "target/node/node";
        Runtime.getRuntime().exec(nodeFromMavenPlugin);
        return nodeFromMavenPlugin;
      } catch (IOException e) {
        return "node";
      }
    }

    private static String resourceScript(String script) {
      try {
        return new File(TestLinterCommandProvider.class.getResource(script).toURI()).getAbsolutePath();
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e);
      }
    }

    TestLinterCommandProvider nodeScript(String script, String args) {
      this.elements = new String[]{ nodeExecutable, resourceScript(script), args};
      return this;
    }

    @Override
    public String[] commandParts(File deployDestination, SensorContext context) {
      return elements;
    }

    @Override
    public String configPath(File deployDestination) {
      return new File(deployDestination, "testconfig.json").getAbsolutePath();
    }

    @Override
    public String nodeExecutable(Configuration configuration) {
      return nodeExecutable;
    }
  }

  private static class TestBundleHandler implements BundleHandler {

    @Override
    public void deployBundle(File deployDestination) {
      // do nothing
    }
  }
}
