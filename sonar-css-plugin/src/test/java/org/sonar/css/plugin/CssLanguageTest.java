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
package org.sonar.css.plugin;

import org.junit.Test;
import org.sonar.api.config.internal.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;

public class CssLanguageTest {

  @Test
  public void test() {
    MapSettings settings = new MapSettings();
    settings.setProperty(CssPlugin.FILE_SUFFIXES_KEY, CssPlugin.FILE_SUFFIXES_DEFVALUE);
    CssLanguage language = new CssLanguage(settings.asConfig());
    assertThat(language.getKey()).isEqualTo("css");
    assertThat(language.getName()).isEqualTo("CSS");
    assertThat(language.getFileSuffixes()).containsOnly(".css", ".less", ".scss");
  }
}
