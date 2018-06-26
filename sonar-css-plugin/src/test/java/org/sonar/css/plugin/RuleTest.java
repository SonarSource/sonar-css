package org.sonar.css.plugin;

import java.lang.reflect.InvocationTargetException;
import org.junit.Test;
import org.sonar.css.plugin.rules.CssRule;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleTest {

  @Test
  public void class_name_should_match_stylelint_key() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    for (Class ruleClass : CssRules.getRuleClasses()) {
      CssRule rule = (CssRule)ruleClass.getConstructor().newInstance();
      String stylelintRuleKeyWithoutUnderscore = rule.stylelintKey().replace("-", "");
      assertThat(ruleClass.getSimpleName()).isEqualToIgnoringCase(stylelintRuleKeyWithoutUnderscore);
    }
  }
}
