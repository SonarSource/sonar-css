/*
 * SonarCSS
 * Copyright (C) 2018-2021 SonarSource SA
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

import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.SonarEdition;
import org.sonar.api.SonarQubeSide;
import org.sonar.api.SonarRuntime;
import org.sonar.api.internal.SonarRuntimeImpl;
import org.sonar.api.utils.Version;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeDeprecationWarningTest {

  private static final String MSG = "You are using Node.js version 8, which reached end-of-life. Support for this version will be dropped in future release, please upgrade Node.js to more recent version.";

  @Rule
  public final LogTester logTester = new LogTester();

  @Test
  public void test() {
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarQube(Version.create(8, 5), SonarQubeSide.SCANNER, SonarEdition.COMMUNITY);
    List<String> warnings = new ArrayList<>();
    NodeDeprecationWarning deprecationWarning = new NodeDeprecationWarning(sonarRuntime, warnings::add);
    deprecationWarning.logNodeDeprecation(8);

    assertThat(warnings).containsExactly(MSG);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains(MSG);
  }

  @Test
  public void test_good_version() {
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarQube(Version.create(8, 5), SonarQubeSide.SCANNER, SonarEdition.COMMUNITY);
    List<String> warnings = new ArrayList<>();
    NodeDeprecationWarning deprecationWarning = new NodeDeprecationWarning(sonarRuntime, warnings::add);
    deprecationWarning.logNodeDeprecation(12);

    assertThat(warnings).isEmpty();
    assertThat(logTester.logs(LoggerLevel.WARN)).isEmpty();
  }

  @Test
  public void test_sonarcloud() {
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarQube(Version.create(8, 5), SonarQubeSide.SCANNER, SonarEdition.SONARCLOUD);
    List<String> warnings = new ArrayList<>();
    NodeDeprecationWarning deprecationWarning = new NodeDeprecationWarning(sonarRuntime, warnings::add);
    deprecationWarning.logNodeDeprecation(8);

    assertThat(warnings).isEmpty();
    assertThat(logTester.logs(LoggerLevel.WARN)).contains(MSG);
  }

  @Test
  public void test_no_warnings() {
    // SonarLint doesn't provide AnalysisWarnings API
    SonarRuntime sonarRuntime = SonarRuntimeImpl.forSonarLint(Version.create(8, 5));
    NodeDeprecationWarning deprecationWarning = new NodeDeprecationWarning(sonarRuntime);
    deprecationWarning.logNodeDeprecation(8);

    assertThat(logTester.logs(LoggerLevel.WARN)).contains(MSG);
  }

}
