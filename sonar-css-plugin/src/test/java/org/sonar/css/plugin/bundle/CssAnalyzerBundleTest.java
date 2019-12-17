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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.utils.internal.JUnitTempFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CssAnalyzerBundleTest {

  @Rule
  public JUnitTempFolder tempFolder = new JUnitTempFolder();

  @Test
  public void default_css_bundle_location() throws Exception {
    CssAnalyzerBundle bundle = new CssAnalyzerBundle(tempFolder);
    assertThat(bundle.bundleLocation).isEqualTo("/css-bundle.zip");
    assertThat(bundle.deployLocation.toString()).endsWith("bundles");
  }

  @Test
  public void almost_empty_css_bundle() throws Exception {
    Bundle bundle = new CssAnalyzerBundle("/bundle/test-css-bundle.zip", tempFolder);
    bundle.deploy();
    String script = bundle.startServerScript();
    File scriptFile = new File(script);
    assertThat(scriptFile).exists();
    String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
    assertThat(content).startsWith("#!/usr/bin/env node");
  }

  @Test
  public void missing_bundle() throws Exception {
    Bundle bundle = new CssAnalyzerBundle("/bundle/invalid-bundle-path.zip", tempFolder);
    assertThatThrownBy(bundle::deploy)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("css-bundle not found in /bundle/invalid-bundle-path.zip");
  }

  @Test
  public void invalid_bundle_zip() throws Exception {
    Bundle bundle = new CssAnalyzerBundle("/bundle/invalid-zip-file.zip", tempFolder);
    assertThatThrownBy(bundle::deploy)
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Failed to deploy css-bundle (with classpath '/bundle/invalid-zip-file.zip')");
  }

  @Test
  public void should_not_fail_when_deployed_twice() throws Exception {
    Bundle bundle = new CssAnalyzerBundle("/bundle/test-css-bundle.zip", tempFolder);
    bundle.deploy();
    bundle.deploy();
    // no exception expected
  }
}
