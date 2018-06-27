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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

public class StylelintCommandProviderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public final LogTester logTester = new LogTester();

  @Test
  public void test() throws Exception {
    StylelintCommandProvider stylelintCommandProvider = new StylelintCommandProvider();
    File deployDestination = new File("deploy_destination");
    File baseDir = new File("src/test/resources").getAbsoluteFile();
    SensorContextTester context = SensorContextTester.create(baseDir);
    context.settings().setProperty(CssPlugin.FILE_SUFFIXES_KEY, ".foo,.bar");
    assertThat(stylelintCommandProvider.commandParts(deployDestination, context)).containsExactly(
      "node",
      new File(deployDestination, "css-bundle/node_modules/stylelint/bin/stylelint").getAbsolutePath(),
      baseDir.getAbsolutePath() + File.separator + "**" + File.separator + "*{.foo,.bar}",
      "--config",
      new File(deployDestination, "css-bundle/stylelintconfig.json").getAbsolutePath(),
      "-f",
      "json"
    );
  }

  @Test
  public void test_node_executable_wo_settings() throws Exception {
    StylelintCommandProvider stylelintCommandProvider = new StylelintCommandProvider();
    MapSettings settings = new MapSettings();
    assertThat(stylelintCommandProvider.nodeExecutable(settings.asConfig())).isEqualTo("node");
    assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty();
  }

  @Test
  public void test_node_executable_custom() throws Exception {
    StylelintCommandProvider stylelintCommandProvider = new StylelintCommandProvider();
    MapSettings settings = new MapSettings();
    File customNode = temporaryFolder.newFile("custom-node.exe");
    settings.setProperty(CssPlugin.NODE_EXECUTABLE, customNode.getAbsolutePath());
    assertThat(stylelintCommandProvider.nodeExecutable(settings.asConfig())).isEqualTo(customNode.getAbsolutePath());
    assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty();
  }

  @Test
  public void test_node_executable_custom_invalid() throws Exception {
    StylelintCommandProvider stylelintCommandProvider = new StylelintCommandProvider();

    MapSettings settings = new MapSettings();
    settings.setProperty(CssPlugin.NODE_EXECUTABLE, "mynode");
    assertThat(stylelintCommandProvider.nodeExecutable(settings.asConfig())).isEqualTo("node");
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Provided node executable file does not exist: mynode. Default 'node' will be used.");
  }
}
