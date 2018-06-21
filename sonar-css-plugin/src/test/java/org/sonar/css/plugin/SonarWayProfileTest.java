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

import org.junit.Test;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.BuiltInQualityProfile;
import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition.Context;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarWayProfileTest {

  @Test
  public void should_create_sonar_way_profile() {
    SonarWayProfile definition = new SonarWayProfile();
    Context context = new Context();
    definition.define(context);

    BuiltInQualityProfile profile = context.profile("css", SonarWayProfile.PROFILE_NAME);

    assertThat(profile.language()).isEqualTo(CssLanguage.KEY);
    assertThat(profile.name()).isEqualTo(SonarWayProfile.PROFILE_NAME);
    assertThat(profile.rules()).extracting("repoKey").containsOnly(CssRulesDefinition.REPOSITORY_KEY);
    assertThat(profile.rules()).extracting("ruleKey").hasSize(CssRules.getRuleClasses().size());
  }

}
