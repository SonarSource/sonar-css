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
package org.sonar.css.its;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.Issues.Issue;
import org.sonarqube.ws.client.issues.SearchRequest;
import org.sonarsource.analyzer.commons.ProfileGenerator;
import org.sonarsource.analyzer.commons.ProfileGenerator.RulesConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.css.its.Tests.newWsClient;

public class IssuesTest {

  private static String PROJECT_KEY = "issues-project";

  @ClassRule
  public static Orchestrator orchestrator = Tests.ORCHESTRATOR;

  @BeforeClass
  public static void prepare() {
    RulesConfiguration rulesConfiguration = new RulesConfiguration();
    rulesConfiguration.add("S4670", "ignoreTypes", "/^mat-/, /^fa-/");
    File profile = ProfileGenerator.generateProfile(orchestrator.getServer().getUrl(), "css", "css", rulesConfiguration, Collections.emptySet());
    orchestrator.getServer().restoreProfile(FileLocation.of(profile));

    orchestrator.getServer().provisionProject(PROJECT_KEY, PROJECT_KEY);
    orchestrator.getServer().associateProjectToQualityProfile(PROJECT_KEY, "css", "rules");

    SonarScanner scanner = Tests.createScanner(PROJECT_KEY);
    scanner.setProperty("sonar.html.file.suffixes", ".htm");
    orchestrator.executeBuild(scanner);
  }

  @Test
  public void test() {
    SearchRequest request = new SearchRequest();
    request.setComponentKeys(Collections.singletonList(PROJECT_KEY));
    List<Issue> issuesList = newWsClient().issues().search(request).getIssuesList().stream()
      .filter(i -> i.getRule().startsWith("css:"))
      .collect(Collectors.toList());

    assertThat(issuesList).extracting(Issue::getRule, Issue::getComponent).containsExactlyInAnyOrder(
      tuple("css:S4662", "issues-project:src/cssModules.css"),
      tuple("css:S4667", "issues-project:src/empty1.css"),
      tuple("css:S4667", "issues-project:src/empty2.less"),
      tuple("css:S4667", "issues-project:src/empty3.scss"),
      tuple("css:S1128", "issues-project:src/file1.css"),
      tuple("css:S1116", "issues-project:src/file1.css"),
      tuple("css:S4664", "issues-project:src/file1.css"),
      tuple("css:S4660", "issues-project:src/file1.css"),
      tuple("css:S4659", "issues-project:src/file1.css"),
      tuple("css:S4647", "issues-project:src/file1.css"),
      tuple("css:S4663", "issues-project:src/file1.css"),
      tuple("css:S4652", "issues-project:src/file1.css"),
      tuple("css:S4656", "issues-project:src/file1.css"),
      tuple("css:S4649", "issues-project:src/file1.css"),
      tuple("css:S4648", "issues-project:src/file1.css"),
      tuple("css:S4654", "issues-project:src/file1.css"),
      tuple("css:S4657", "issues-project:src/file1.css"),
      tuple("css:S4650", "issues-project:src/file1.css"),
      tuple("css:S4653", "issues-project:src/file1.css"),
      tuple("css:S4668", "issues-project:src/file1.css"),
      tuple("css:S4654", "issues-project:src/file1.css"),
      tuple("css:S4651", "issues-project:src/file1.css"),
      tuple("css:S4666", "issues-project:src/file1.css"),
      tuple("css:S4670", "issues-project:src/file1.css"),
      tuple("css:S4662", "issues-project:src/file1.css"),
      tuple("css:S4655", "issues-project:src/file1.css"),
      tuple("css:S4658", "issues-project:src/file1.css"),
      tuple("css:S4661", "issues-project:src/file1.css"),
      tuple("css:S1128", "issues-project:src/file2.less"),
      tuple("css:S1116", "issues-project:src/file2.less"),
      tuple("css:S4664", "issues-project:src/file2.less"),
      tuple("css:S4660", "issues-project:src/file2.less"),
      tuple("css:S4659", "issues-project:src/file2.less"),
      tuple("css:S4647", "issues-project:src/file2.less"),
      tuple("css:S4663", "issues-project:src/file2.less"),
      tuple("css:S4652", "issues-project:src/file2.less"),
      tuple("css:S4656", "issues-project:src/file2.less"),
      tuple("css:S4649", "issues-project:src/file2.less"),
      tuple("css:S4648", "issues-project:src/file2.less"),
      tuple("css:S4654", "issues-project:src/file2.less"),
      tuple("css:S4657", "issues-project:src/file2.less"),
      tuple("css:S4650", "issues-project:src/file2.less"),
      tuple("css:S4653", "issues-project:src/file2.less"),
      tuple("css:S4651", "issues-project:src/file2.less"),
      tuple("css:S4666", "issues-project:src/file2.less"),
      tuple("css:S4670", "issues-project:src/file2.less"),
      tuple("css:S4662", "issues-project:src/file2.less"),
      tuple("css:S4655", "issues-project:src/file2.less"),
      tuple("css:S4658", "issues-project:src/file2.less"),
      tuple("css:S4661", "issues-project:src/file2.less"),
      tuple("css:S1128", "issues-project:src/file3.scss"),
      tuple("css:S1116", "issues-project:src/file3.scss"),
      tuple("css:S4664", "issues-project:src/file3.scss"),
      tuple("css:S4660", "issues-project:src/file3.scss"),
      tuple("css:S4659", "issues-project:src/file3.scss"),
      tuple("css:S4647", "issues-project:src/file3.scss"),
      tuple("css:S4663", "issues-project:src/file3.scss"),
      tuple("css:S4652", "issues-project:src/file3.scss"),
      tuple("css:S4656", "issues-project:src/file3.scss"),
      tuple("css:S4649", "issues-project:src/file3.scss"),
      tuple("css:S4648", "issues-project:src/file3.scss"),
      tuple("css:S4654", "issues-project:src/file3.scss"),
      tuple("css:S4657", "issues-project:src/file3.scss"),
      tuple("css:S4650", "issues-project:src/file3.scss"),
      tuple("css:S4653", "issues-project:src/file3.scss"),
      tuple("css:S4651", "issues-project:src/file3.scss"),
      tuple("css:S4666", "issues-project:src/file3.scss"),
      tuple("css:S4670", "issues-project:src/file3.scss"),
      tuple("css:S4662", "issues-project:src/file3.scss"),
      tuple("css:S4655", "issues-project:src/file3.scss"),
      tuple("css:S4658", "issues-project:src/file3.scss"),
      tuple("css:S4661", "issues-project:src/file3.scss"),
      tuple("css:S1116", "issues-project:src/file5.htm")
    );
  }

}
