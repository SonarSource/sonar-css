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
package org.sonar.css.plugin.server;

import java.io.IOException;
import org.sonar.api.Startable;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.scanner.ScannerSide;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.sonarsource.api.sonarlint.SonarLintSide.MULTIPLE_ANALYSES;

@ScannerSide
@SonarLintSide(lifespan = MULTIPLE_ANALYSES)
public interface AnalyzerBridgeServer extends Startable {

  void startServerLazily(SensorContext context) throws IOException;

  Issue[] analyze(Request request) throws IOException;

  String getCommandInfo();

  boolean isAlive();

  class Request {
    public final String filePath;
    public final String configFile;

    public Request(String filePath, String configFile) {
      this.filePath = filePath;
      this.configFile = configFile;
    }
  }

  class Issue {
    public final Integer line;
    public final String rule;
    public final String text;

    public Issue(Integer line, String rule, String text) {
      this.line = line;
      this.rule = rule;
      this.text = text;
    }
  }

}
