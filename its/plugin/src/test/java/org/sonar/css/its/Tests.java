package org.sonar.css.its;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  MetricsTest.class
})
public class Tests {

  private static final FileLocation PLUGIN_LOCATION = FileLocation.byWildcardMavenFilename(
    new File("../../sonar-typescript-plugin/target"), "sonar-css-plugin-*.jar");

  @ClassRule
  public static final Orchestrator ORCHESTRATOR;

  static {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv();
    orchestratorBuilder.addPlugin(PLUGIN_LOCATION);
    ORCHESTRATOR = orchestratorBuilder.build();
  }

//  public static WsClient newWsClient() {
//    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
//      .url(ORCHESTRATOR.getServer().getUrl())
//      .build());
//  }

//  public static Double getProjectMeasureAsDouble(String metricKey, String projectKey) {
//    Measure measure = getMeasure(metricKey, projectKey);
//    return (measure == null) ? null : Double.parseDouble(measure.getValue());
//  }

//  private static Measure getMeasure(String metricKey, String projectKey) {
//    ComponentWsResponse response = newWsClient().measures().component(new ComponentRequest()
//      .setComponent(projectKey)
//      .setMetricKeys(singletonList(metricKey)));
//    List<Measure> measures = response.getComponent().getMeasuresList();
//    return measures.size() == 1 ? measures.get(0) : null;
//  }


  public static SonarScanner createScanner(String location, String projectKey) {
    File projectDir = FileLocation.of(location).getFile();

    return SonarScanner.create()
      .setSourceEncoding("UTF-8")
      .setProjectDir(projectDir)
      .setProjectKey(projectKey)
      .setProjectName(projectKey)
      .setProjectVersion("1.0")
      .setSourceDirs("src");
  }

}
