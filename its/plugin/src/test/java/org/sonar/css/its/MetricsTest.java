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
package org.sonar.css.its;

import com.sonar.orchestrator.Orchestrator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.css.its.Tests.getMeasure;
import static org.sonar.css.its.Tests.getProjectMeasureAsDouble;

public class MetricsTest {

  private static String PROJECT_KEY = "metrics-project";

  @ClassRule
  public static Orchestrator orchestrator = Tests.ORCHESTRATOR;

  @BeforeClass
  public static void prepare() {
    orchestrator.executeBuild(Tests.createScanner(PROJECT_KEY));
  }

  @Test
  public void test() {
    assertThat(getProjectMeasureAsDouble("lines", PROJECT_KEY)).isEqualTo(43);
    assertThat(getProjectMeasureAsDouble("ncloc", PROJECT_KEY)).isEqualTo(32);
    assertThat(getMeasure("ncloc_language_distribution", PROJECT_KEY).getValue()).isEqualTo("css=22;web=10");
    assertThat(getProjectMeasureAsDouble("comment_lines", PROJECT_KEY)).isEqualTo(4);

    assertThat(getMeasure("ncloc_data", PROJECT_KEY + ":src/file1.css").getValue())
        .contains("1=1;", "2=1;", "3=1;", "4=1;", "5=1;", "6=1;", "7=1");

    assertThat(getMeasure("ncloc_data", PROJECT_KEY + ":src/file2.less").getValue())
        .contains("1=1;", "2=1;", "3=1;", "4=1;", "5=1;", "6=1;", "7=1;", "8=1;", "9=1");

    assertThat(getMeasure("ncloc_data", PROJECT_KEY + ":src/file3.scss").getValue())
        .contains("1=1;", "3=1;", "5=1;", "6=1;", "7=1;", "8=1");
  }

}
