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
import java.nio.file.Paths;
import java.util.Optional;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

@ScannerSide
public class StylelintCommandProvider implements LinterCommandProvider {

  private static final Logger LOG = Loggers.get(StylelintCommandProvider.class);

  private static final String CONFIG_PATH = "css-bundle/stylelintconfig.json";

  @Override
  public String[] commandParts(File deployDestination, SensorContext context) {
    String projectBaseDir = context.fileSystem().baseDir().getAbsolutePath();
    String[] suffixes = context.config().getStringArray(CssPlugin.FILE_SUFFIXES_KEY);
    String filesGlob = "**" + File.separator + "*{" + String.join(",", suffixes) + "}";
    String filesToAnalyze = Paths.get(projectBaseDir, "TOREPLACE").toString();
    filesToAnalyze = filesToAnalyze.replace("TOREPLACE", filesGlob);

    return new String[]{
      nodeExecutable(context.config()),
      new File(deployDestination, "css-bundle/node_modules/stylelint/bin/stylelint").getAbsolutePath(),
      filesToAnalyze,
      "--config", new File(deployDestination, CONFIG_PATH).getAbsolutePath(),
      "-f", "json"
    };
  }

  @Override
  public String configPath(File deployDestination) {
    return new File(deployDestination, CONFIG_PATH).getAbsolutePath();
  }

  @Override
  public String nodeExecutable(Configuration configuration) {
    Optional<String> nodeExecutableOptional = configuration.get(CssPlugin.NODE_EXECUTABLE);
    if (nodeExecutableOptional.isPresent()) {
      String nodeExecutable = nodeExecutableOptional.get();
      File file = new File(nodeExecutable);
      if (file.exists()) {
        return nodeExecutable;
      }
      LOG.warn("Provided node executable file does not exist: " + file);
    }
    return CssPlugin.NODE_EXECUTABLE_DEFAULT;
  }
}
