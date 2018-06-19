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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonar.css.plugin.rules.ColorNoInvalidHex;
import org.sonar.css.plugin.rules.UnitNoUnknown;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

import static org.sonar.css.plugin.SonarWayProfile.PROFILE_PATH;

public class CssRulesDefinition implements RulesDefinition {

  public static final String REPOSITORY_KEY = "css";
  public static final String RULE_REPOSITORY_NAME = "SonarAnalyzer";

  public static final String RESOURCE_FOLDER = "org/sonar/l10n/css/rules/css";

  public static final List<Class> RULE_CLASSES = Collections.unmodifiableList(Arrays.asList(
    ColorNoInvalidHex.class,
    UnitNoUnknown.class
  ));

  @Override
  public void define(Context context) {
    NewRepository repository = context
      .createRepository(REPOSITORY_KEY, CssLanguage.KEY)
      .setName(RULE_REPOSITORY_NAME);

    RuleMetadataLoader ruleMetadataLoader = new RuleMetadataLoader(RESOURCE_FOLDER, PROFILE_PATH);
    ruleMetadataLoader.addRulesByAnnotatedClass(repository, RULE_CLASSES);
    repository.done();
  }
}
