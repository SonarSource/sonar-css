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

import java.io.InputStream;
import java.nio.file.Path;
import org.sonar.api.scanner.ScannerSide;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.css.plugin.Zip;
import org.sonarsource.api.sonarlint.SonarLintSide;

import static org.sonarsource.api.sonarlint.SonarLintSide.MULTIPLE_ANALYSES;

@ScannerSide
@SonarLintSide(lifespan = MULTIPLE_ANALYSES)
public class CssAnalyzerBundle implements Bundle {

  private static final Logger LOG = Loggers.get(CssAnalyzerBundle.class);
  private static final Profiler PROFILER = Profiler.createIfDebug(LOG);

  // this archive is created in css-bundle module
  private static final String DEFAULT_BUNDLE_LOCATION = "/css-bundle.zip";
  private static final String DEFAULT_STARTUP_SCRIPT = "css-bundle/bin/server";

  final String bundleLocation;
  final Path deployLocation;

  public CssAnalyzerBundle(TempFolder tempFolder) {
    this(DEFAULT_BUNDLE_LOCATION, tempFolder);
  }

  CssAnalyzerBundle(String bundleLocation, TempFolder tempFolder) {
    this.bundleLocation = bundleLocation;
    this.deployLocation = tempFolder.newDir("bundles").toPath();
  }

  @Override
  public void deploy() {
    PROFILER.startDebug("Deploying bundle");
    LOG.debug("Deploying css-bundle into {}", deployLocation);
    InputStream bundle = getClass().getResourceAsStream(bundleLocation);
    if (bundle == null) {
      throw new IllegalStateException("css-bundle not found in " + bundleLocation);
    }
    try {
      LOG.debug("Deploying css-bundle to {}", deployLocation.toAbsolutePath());
      Zip.extract(bundle, deployLocation.toFile());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deploy css-bundle (with classpath '" + bundleLocation + "')", e);
    }
    PROFILER.stopDebug();
  }

  @Override
  public String startServerScript() {
    return resolve(DEFAULT_STARTUP_SCRIPT);
  }

  @Override
  public String resolve(String relativePath) {
    return deployLocation.resolve(relativePath).toAbsolutePath().toString();
  }

}
