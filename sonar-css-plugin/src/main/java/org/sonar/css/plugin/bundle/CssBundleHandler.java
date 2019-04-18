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
package org.sonar.css.plugin.bundle;

import java.io.File;
import java.io.InputStream;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.css.plugin.Zip;

@ScannerSide
public class CssBundleHandler implements BundleHandler {

  private static final String BUNDLE_LOCATION = "/css-bundle.zip";
  private static final Logger LOG = Loggers.get(CssBundleHandler.class);
  String bundleLocation = BUNDLE_LOCATION;

  /**
   * Extracting "css-bundle.zip" (containing stylelint)
   * to deployDestination (".sonar" directory of the analyzed project).
   */
  @Override
  public void deployBundle(File deployDestination) {
    InputStream bundle = getClass().getResourceAsStream(bundleLocation);
    if (bundle == null) {
      throw new IllegalStateException("CSS bundle not found at " + bundleLocation);
    }
    try {
      LOG.debug("Deploying bundle to {}", deployDestination.getAbsolutePath());
      Zip.extract(bundle, deployDestination);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deploy CSS bundle (with classpath '" + bundleLocation + "')", e);
    }
  }

}
