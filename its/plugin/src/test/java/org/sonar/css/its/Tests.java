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
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.sonarqube.ws.Measures.ComponentWsResponse;
import org.sonarqube.ws.Measures.Measure;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.measures.ComponentRequest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  MetricsTest.class,
  IssuesTest.class,
  PhpProjectTest.class,
  StylelintReportTest.class,
  MinifiedTest.class
})
public class Tests {

  private static final FileLocation PLUGIN_LOCATION = FileLocation.byWildcardMavenFilename(
    new File("../../sonar-css-plugin/target"), "sonar-css-plugin-*.jar");


  @ClassRule
  public static final Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .addPlugin(PLUGIN_LOCATION)
      .setSonarVersion(System.getProperty("sonar.runtimeVersion", "LATEST_RELEASE"));
    ORCHESTRATOR = orchestratorBuilder.build();
  }

  public static WsClient newWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(ORCHESTRATOR.getServer().getUrl())
      .build());
  }

  public static Double getProjectMeasureAsDouble(String metricKey, String projectKey) {
    Measure measure = getMeasure(metricKey, projectKey);
    return (measure == null) ? null : Double.parseDouble(measure.getValue());
  }

  public static Measure getMeasure(String metricKey, String projectKey) {
    ComponentWsResponse response = newWsClient().measures().component(new ComponentRequest()
      .setComponent(projectKey)
      .setMetricKeys(Collections.singletonList(metricKey)));
    List<Measure> measures = response.getComponent().getMeasuresList();
    return measures.size() == 1 ? measures.get(0) : null;
  }

  public static SonarScanner createScanner(String projectKey) {
    File projectDir = FileLocation.of("projects" + File.separator + projectKey).getFile();

    return SonarScanner.create()
      .setSourceEncoding("UTF-8")
      .setProjectDir(projectDir)
      .setProjectKey(projectKey)
      .setProjectName(projectKey)
      .setProjectVersion("1.0")
      .setSourceDirs("src");
  }

}
