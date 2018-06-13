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

import java.io.File;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile.Type;
import org.sonar.api.batch.rule.ActiveRules;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.css.plugin.CssRuleSensor.Issue;
import org.sonar.css.plugin.CssRuleSensor.IssuesPerFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CssRuleSensorTest {

  private CheckFactory checkFactory = new CheckFactory(mock(ActiveRules.class));
  private File BASE_DIR = new File("src/test/resources").getAbsoluteFile();

  @Test
  public void test_descriptor() throws Exception {
    CssRuleSensor sensor = new CssRuleSensor(new CssBundleHandler(), checkFactory);
    DefaultSensorDescriptor sensorDescriptor = new DefaultSensorDescriptor();
    sensor.describe(sensorDescriptor);
    assertThat(sensorDescriptor.name()).isEqualTo("SonarCSS Rules");
    assertThat(sensorDescriptor.languages()).containsOnly("css");
    assertThat(sensorDescriptor.type()).isEqualTo(Type.MAIN);
  }

  @Test
  public void test_execute() throws Exception {
    CssRuleSensor sensor = new CssRuleSensor(new CssBundleHandler(), checkFactory);
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
    sensor.execute(context);

    assertThat(context.allIssues()).hasSize(1);
  }

  @Test
  public void test_save_issues() throws Exception {
    IssuesPerFile issuesPerFile = new IssuesPerFile();
    Issue issue1 = new Issue(2, "ruleKey1", "issue message1");
    Issue issue2 = new Issue(1, "ruleKey2", "issue message2");
    Issue[] issues = new Issue[]{ issue1, issue2 };
    issuesPerFile.warnings = issues;
    issuesPerFile.source = "foo";

    CssRuleSensor sensor = new CssRuleSensor(new CssBundleHandler(), checkFactory);
    SensorContextTester context = SensorContextTester.create(BASE_DIR);
//    sensor.saveIssues(context, issuesPerFile);
  }
}
