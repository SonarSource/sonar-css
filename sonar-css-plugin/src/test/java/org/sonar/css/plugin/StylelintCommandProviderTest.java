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
import org.junit.Test;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import static org.assertj.core.api.Assertions.assertThat;

public class StylelintCommandProviderTest {

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
}
