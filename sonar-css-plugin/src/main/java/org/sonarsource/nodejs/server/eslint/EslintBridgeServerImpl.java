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
package org.sonarsource.nodejs.server.eslint;

import java.io.IOException;
import org.sonar.api.config.Configuration;
import org.sonarsource.nodejs.NodeCommandBuilder;
import org.sonarsource.nodejs.server.AnalyzerBridgeServerImpl;
import org.sonarsource.nodejs.server.Bundle;

public class EslintBridgeServerImpl extends AnalyzerBridgeServerImpl {

  public EslintBridgeServerImpl(Configuration configuration, NodeCommandBuilder nodeCommandBuilder, int timeoutSeconds, Bundle bundle) {
    super(configuration, nodeCommandBuilder, timeoutSeconds, bundle);
  }

  public AnalysisResponse analyzeJavaScript(AnalysisRequest request) throws IOException {
    return analyze("analyze-js", request);
  }

  public AnalysisResponse analyzeTypeScript(AnalysisRequest request) throws IOException {
    return analyze("analyze-ts", request);
  }

}
