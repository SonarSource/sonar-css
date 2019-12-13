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
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.log.LogTester;
import org.sonarsource.nodejs.NodeCommand;

import static org.assertj.core.api.Assertions.assertThat;

public class StylelintCommandProviderTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public final LogTester logTester = new LogTester();

  @Test
  public void test() throws IOException {
    StylelintCommandProvider stylelintCommandProvider = new StylelintCommandProvider();
    File deployDestination = new File("deploy_destination");
    File baseDir = new File("src/test/resources").getAbsoluteFile();
    SensorContextTester context = SensorContextTester.create(baseDir);
    context.settings().setProperty(CssPlugin.FILE_SUFFIXES_KEY, ".foo,.bar")
      .setProperty("sonar.javascript.file.suffixes", ".js")
      .setProperty("sonar.php.file.suffixes", ".php")
      .setProperty("sonar.java.file.suffixes", ".java");
    Consumer<String> noop = a -> {};
    NodeCommand nodeCommand = stylelintCommandProvider.nodeCommand(deployDestination, context, noop, noop);
    assertThat(nodeCommand.toString()).endsWith(
      String.join(" ",
      new File(deployDestination, "css-bundle/node_modules/stylelint/bin/stylelint").getAbsolutePath(),
      baseDir.getAbsolutePath() + File.separator + "**" + File.separator + "*{.foo,.bar,.php}",
      "--config",
      new File(deployDestination, "css-bundle/stylelintconfig.json").getAbsolutePath(),
      "-f",
      "json")
    );
  }
}
