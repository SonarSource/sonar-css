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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.css.plugin.bundle.BundleHandler;
import org.sonar.css.plugin.bundle.CssBundleHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class CssRuleSensorTest {

  private static CheckFactory checkFactory = new CheckFactory(new TestActiveRules("S4647"));
  private File BASE_DIR = new File("src/test/resources").getAbsoluteFile();

  @Rule
  public TemporaryFolder tmpDir = new TemporaryFolder();

  @Test
  public void test_descriptor() throws Exception {
    CssRuleSensor sensor = new CssRuleSensor(new CssBundleHandler(), checkFactory, new StylelintCommandProvider());
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("SonarCSS Rules");
    assertThat(sensorDescriptor.languages()).containsOnly("css");
    assertThat(sensorDescriptor.type()).isEqualTo(Type.MAIN);
  }

  @Test
  public void test_execute() throws Exception {
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    context.fileSystem().setWorkDir(tmpDir.getRoot().toPath());
    DefaultInputFile inputFile = createInputFile(context, "some css content\n on 2 lines", "dir/file.css");
    TestLinterCommandProvider rulesExecution = TestLinterCommandProvider.nodeScript("/executables/mockStylelint.js", inputFile.absolutePath());
    CssRuleSensor sensor = new CssRuleSensor(new TestBundleHandler(), checkFactory, rulesExecution);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);

    Path configPath = Paths.get(context.fileSystem().workDir().getAbsolutePath(), "testconfig.json");
    assertThat(Files.readAllLines(configPath)).containsOnly("{\"rules\":{\"color-no-invalid-hex\":true}}");
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

  private static class TestLinterCommandProvider implements LinterCommandProvider {

    private static String nodeExecutable = findNodeExecutable();

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

    static TestLinterCommandProvider nodeScript(String script, String args) {
      TestLinterCommandProvider testRulesExecution = new TestLinterCommandProvider();
      testRulesExecution.elements = new String[]{ nodeExecutable, resourceScript(script), args};
      return testRulesExecution;
    }

    @Override
    public String[] commandParts(File deployDestination, File projectBaseDir) {
      return elements;
    }

    @Override
    public String configPath(File deployDestination) {
      return new File(deployDestination, "testconfig.json").getAbsolutePath();
    }
  }

  private static class TestBundleHandler implements BundleHandler {

    @Override
    public void deployBundle(File deployDestination) {
      // do nothing
    }
  }
}
