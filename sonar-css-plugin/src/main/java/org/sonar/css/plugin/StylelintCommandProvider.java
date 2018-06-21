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
import org.sonar.api.batch.ScannerSide;

@ScannerSide
public class StylelintCommandProvider implements LinterCommandProvider {

  private final String CONFIG_PATH = "css-bundle/stylelintconfig.json";

  @Override
  public String[] commandParts(File deployDestination, File projectBaseDir) {
    return new String[]{
      "node",
      new File(deployDestination, "css-bundle/node_modules/stylelint/bin/stylelint").getAbsolutePath(),
      projectBaseDir.getAbsolutePath(),
      "--config", new File(deployDestination, CONFIG_PATH).getAbsolutePath(),
      "-f", "json"
    };
  }

  @Override
  public String configPath(File deployDestination) {
    return new File(deployDestination, CONFIG_PATH).getAbsolutePath();
  }
}
