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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class CssBundleHandlerTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private File DEPLOY_DESTINATION;

  @Before
  public void setUp() throws Exception {
    DEPLOY_DESTINATION = temporaryFolder.newFolder("deployDestination");
  }

  @Test
  public void test() {
    CssBundleHandler bundleHandler = new CssBundleHandler();
    bundleHandler.bundleLocation = "/bundle/test-bundle.zip";
    bundleHandler.deployBundle(DEPLOY_DESTINATION);

    assertThat(new File(DEPLOY_DESTINATION, "test-bundle.js").exists()).isTrue();
  }

}
