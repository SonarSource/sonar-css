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
package org.sonar.css.plugin.rules;

import com.google.gson.Gson;
import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.sonar.css.plugin.CssRules;

import static org.assertj.core.api.Assertions.assertThat;

public class CssRuleTest {

  @Test
  public void class_name_should_match_stylelint_key() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    for (Class ruleClass : CssRules.getRuleClasses()) {
      CssRule rule = (CssRule)ruleClass.getConstructor().newInstance();
      String stylelintRuleKeyWithoutUnderscore = rule.stylelintKey().replace("-", "");
      assertThat(ruleClass.getSimpleName()).isEqualToIgnoringCase(stylelintRuleKeyWithoutUnderscore);
    }
  }

  @Test
  public void rules_default_json_is_true() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    for (Class ruleClass : CssRules.getRuleClasses()) {
      CssRule rule = (CssRule)ruleClass.getConstructor().newInstance();
      if (rule instanceof AtRuleNoUnknown || rule instanceof DeclarationBlockNoDuplicateProperties) {
        continue;
      }

      String optionsAsJson = new Gson().toJson(rule.stylelintOptions());
      assertThat(optionsAsJson).isEqualTo("true");
    }
  }

  @Test
  public void at_rule_unknown_default() {
    String optionsAsJson = new Gson().toJson(new AtRuleNoUnknown().stylelintOptions());
    assertThat(optionsAsJson).isEqualTo("[true,{\"ignoreAtRules\":[\"content\",\"debug\",\"each\",\"else\",\"for\",\"function\",\"if\",\"include\",\"mixin\",\"return\",\"while\"]}]");
  }

  @Test
  public void at_rule_unknown_custom() {
    AtRuleNoUnknown instance = new AtRuleNoUnknown();
    instance.ignoredAtRules = "foo";
    String optionsAsJson = new Gson().toJson(instance.stylelintOptions());
    assertThat(optionsAsJson).isEqualTo("[true,{\"ignoreAtRules\":[\"foo\"]}]");
  }
}
