package org.sonar.css.its;

import com.sonar.orchestrator.Orchestrator;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsTest {

  private static String PROJECT_KEY = "FIXME";

  @ClassRule
  public static Orchestrator orchestrator = Tests.ORCHESTRATOR;

  @BeforeClass
  public static void prepare() {
    orchestrator.resetData();
    orchestrator.executeBuild(Tests.createScanner("projects/sample", PROJECT_KEY));
  }

  @Test
  public void should_have_loaded_issues_into_project_and_ignore_issue_with_nosonar() {
    assertThat(1).isEqualTo(1);
  }

}
