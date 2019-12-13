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

import org.sonar.api.utils.TempFolder;

public class CssAnalyzerBundle extends BundleImpl {

  // this archive is created in eslint-bridge module
  private static final String BUNDLE_LOCATION = "/css-analyzer-bridge-1.0.0.tgz";
  private static final String DEPLOY_LOCATION = "css-analyzer-bridge-bundle";

  public CssAnalyzerBundle(TempFolder tempFolder) {
    this(tempFolder, BUNDLE_LOCATION);
  }

  CssAnalyzerBundle(TempFolder tempFolder, String bundleLocation) {
    super(tempFolder.newDir(DEPLOY_LOCATION).toPath(), bundleLocation, "css-analyzer-bridge");
  }

}
