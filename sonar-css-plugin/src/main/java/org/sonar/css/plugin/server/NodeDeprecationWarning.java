/*
 * SonarCSS
 * Copyright (C) 2018-2021 SonarSource SA
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

import javax.annotation.Nullable;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarProduct;
import org.sonar.api.SonarRuntime;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.scanner.ScannerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;

@ScannerSide
@SonarLintSide(lifespan = SonarLintSide.MULTIPLE_ANALYSES)
public class NodeDeprecationWarning {

  private static final Logger LOG = Loggers.get(NodeDeprecationWarning.class);
  private static final int MIN_RECOMMENDED_NODE_VERSION = 10;
  private final SonarRuntime sonarRuntime;
  private final AnalysisWarnings analysisWarnings;

  public NodeDeprecationWarning(SonarRuntime sonarRuntime) {
    this(sonarRuntime, null);
  }

  public NodeDeprecationWarning(SonarRuntime sonarRuntime, @Nullable AnalysisWarnings analysisWarnings) {
    this.sonarRuntime = sonarRuntime;
    this.analysisWarnings = analysisWarnings;
  }

  void logNodeDeprecation(int actualNodeVersion) {
    if (actualNodeVersion < MIN_RECOMMENDED_NODE_VERSION) {
      String msg = String.format("You are using Node.js version %d, which reached end-of-life. " +
          "Support for this version will be dropped in future release, please upgrade Node.js to more recent version.",
        actualNodeVersion);
      LOG.warn(msg);
      if (isSonarQube() && analysisWarnings != null) {
        analysisWarnings.addUnique(msg);
      }
    }
  }

  private boolean isSonarQube() {
    return sonarRuntime.getProduct() == SonarProduct.SONARQUBE && sonarRuntime.getEdition() != SonarEdition.SONARCLOUD;
  }
}
