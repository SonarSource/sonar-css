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

import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

public class CssPlugin implements Plugin {

  static final String FILE_SUFFIXES_KEY = "sonar.css.file.suffixes";
  public static final String FILE_SUFFIXES_DEFVALUE = ".css,.less,.scss";

  private static final String CSS_CATEGORY = "CSS";
  private static final String GENERAL_SUBCATEGORY = "General";

  @Override
  public void define(Context context) {
    context.addExtensions(
      MetricSensor.class,
      CssLanguage.class,
      SonarWayProfile.class,

      PropertyDefinition.builder(FILE_SUFFIXES_KEY)
        .defaultValue(FILE_SUFFIXES_DEFVALUE)
        .name("File Suffixes")
        .description("List of suffixes for files to analyze.")
        .subCategory(GENERAL_SUBCATEGORY)
        .category(CSS_CATEGORY)
        .onQualifiers(Qualifiers.PROJECT)
        .multiValues(true)
        .build()
    );
  }
}
