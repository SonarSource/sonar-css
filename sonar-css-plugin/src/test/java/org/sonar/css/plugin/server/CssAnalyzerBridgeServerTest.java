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
package org.sonar.css.plugin.server;

import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.internal.JUnitTempFolder;
import org.sonar.api.utils.log.LogTester;
import org.sonar.css.plugin.bundle.Bundle;
import org.sonar.css.plugin.server.AnalyzerBridgeServer.Issue;
import org.sonar.css.plugin.server.AnalyzerBridgeServer.Request;
import org.sonar.css.plugin.server.exception.ServerAlreadyFailedException;
import org.sonarsource.nodejs.NodeCommand;
import org.sonarsource.nodejs.NodeCommandBuilder;
import org.sonarsource.nodejs.NodeCommandException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonar.api.utils.log.LoggerLevel.DEBUG;
import static org.sonar.api.utils.log.LoggerLevel.INFO;
import static org.sonar.api.utils.log.LoggerLevel.WARN;

public class CssAnalyzerBridgeServerTest {

  private static final String START_SERVER_SCRIPT = "startServer.js";
  private static final String CONFIG_FILE = "config.json";
  private static final int TEST_TIMEOUT_SECONDS = 1;

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  @org.junit.Rule
  public final ExpectedException thrown = ExpectedException.none();

  @org.junit.Rule
  public JUnitTempFolder tempFolder = new JUnitTempFolder();

  private SensorContextTester context;
  private CssAnalyzerBridgeServer cssAnalyzerBridgeServer;

  @Before
  public void setUp() throws Exception {
    context = SensorContextTester.create(tempFolder.newDir());
    context.fileSystem().setWorkDir(tempFolder.newDir().toPath());
  }

  @After
  public void tearDown() throws Exception {
    if (cssAnalyzerBridgeServer != null) {
      cssAnalyzerBridgeServer.clean();
    }
  }

  @Test
  public void default_timeout() {
    CssAnalyzerBridgeServer server = new CssAnalyzerBridgeServer(mock(Bundle.class));
    assertThat(server.timeoutSeconds).isEqualTo(60);
  }

  @Test
  public void issue_constructor() {
    Issue issue = new Issue(2, "r", "t");
    assertThat(issue.line).isEqualTo(2);
    assertThat(issue.rule).isEqualTo("r");
    assertThat(issue.text).isEqualTo("t");
  }

  @Test
  public void should_throw_when_not_existing_start_script() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer("NOT_EXISTING.js");

    thrown.expect(NodeCommandException.class);
    thrown.expectMessage("Node.js script to start css-bundle server doesn't exist");

    cssAnalyzerBridgeServer.startServer(context);
  }

  @Test
  public void should_throw_if_failed_to_build_node_command() throws Exception {
    NodeCommandBuilder nodeCommandBuilder = mock(NodeCommandBuilder.class, invocation -> {
      if (NodeCommandBuilder.class.equals(invocation.getMethod().getReturnType())) {
        return invocation.getMock();
      } else {
        throw new NodeCommandException("msg");
      }
    });

    cssAnalyzerBridgeServer = new CssAnalyzerBridgeServer(nodeCommandBuilder, TEST_TIMEOUT_SECONDS, new TestBundle(START_SERVER_SCRIPT));

    thrown.expect(NodeCommandException.class);
    thrown.expectMessage("msg");

    cssAnalyzerBridgeServer.startServerLazily(context);
  }

  @Test
  public void should_forward_process_streams() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    cssAnalyzerBridgeServer.startServerLazily(context);

    assertThat(logTester.logs(DEBUG)).contains("testing debug log");
    assertThat(logTester.logs(WARN)).contains("testing warn log");
    assertThat(logTester.logs(INFO)).contains("testing info log");
  }

  @Test
  public void should_get_answer_from_server() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    cssAnalyzerBridgeServer.startServerLazily(context);

    Request request = new Request("/absolute/path/file.css", CONFIG_FILE);
    Issue[] issues = cssAnalyzerBridgeServer.analyze(request);
    assertThat(issues).hasSize(1);
    assertThat(issues[0].line).isEqualTo(2);
    assertThat(issues[0].rule).isEqualTo("block-no-empty");
    assertThat(issues[0].text).isEqualTo("Unexpected empty block");

    request = new Request("/absolute/path/empty.css", CONFIG_FILE);
    issues = cssAnalyzerBridgeServer.analyze(request);
    assertThat(issues).isEmpty();
  }

  @Test
  public void should_throw_if_failed_to_start() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer("throw.js");

    thrown.expect(NodeCommandException.class);
    thrown.expectMessage("Failed to start server (" + TEST_TIMEOUT_SECONDS + "s timeout)");

    cssAnalyzerBridgeServer.startServerLazily(context);
  }

  @Test
  public void should_return_command_info() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    assertThat(cssAnalyzerBridgeServer.getCommandInfo()).isEqualTo("Node.js command to start css-bundle server was not built yet.");

    cssAnalyzerBridgeServer.startServerLazily(context);
    assertThat(cssAnalyzerBridgeServer.getCommandInfo()).contains("Node.js command to start css-bundle was: ", "node", START_SERVER_SCRIPT);
    assertThat(cssAnalyzerBridgeServer.getCommandInfo()).doesNotContain("--max-old-space-size");
  }

  @Test
  public void should_set_max_old_space_size() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    context.setSettings(new MapSettings().setProperty("sonar.css.node.maxspace", 2048));
    cssAnalyzerBridgeServer.startServerLazily(context);
    assertThat(cssAnalyzerBridgeServer.getCommandInfo()).contains("--max-old-space-size=2048");
  }

  @Test
  public void test_isAlive() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    assertThat(cssAnalyzerBridgeServer.isAlive()).isFalse();
    cssAnalyzerBridgeServer.startServerLazily(context);
    assertThat(cssAnalyzerBridgeServer.isAlive()).isTrue();
    cssAnalyzerBridgeServer.stop();
    assertThat(cssAnalyzerBridgeServer.isAlive()).isFalse();
  }

  @Test
  public void test_lazy_start() throws Exception {
    String alreadyStarted = "css-bundle server is up, no need to start.";
    String starting = "Starting Node.js process to start css-bundle server at port";
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer();
    cssAnalyzerBridgeServer.startServerLazily(context);
    assertThat(logTester.logs(DEBUG).stream().anyMatch(s -> s.startsWith(starting))).isTrue();
    assertThat(logTester.logs(DEBUG)).doesNotContain(alreadyStarted);
    logTester.clear();
    cssAnalyzerBridgeServer.startServerLazily(context);
    assertThat(logTester.logs(DEBUG).stream().noneMatch(s -> s.startsWith(starting))).isTrue();
    assertThat(logTester.logs(DEBUG)).contains(alreadyStarted);
  }

  @Test
  public void should_throw_special_exception_when_failed_already() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer("throw.js");
    String failedToStartExceptionMessage = "Failed to start server (" + TEST_TIMEOUT_SECONDS + "s timeout)";
    assertThatThrownBy(() -> cssAnalyzerBridgeServer.startServerLazily(context))
      .isInstanceOf(NodeCommandException.class)
      .hasMessage(failedToStartExceptionMessage);

    assertThatThrownBy(() -> cssAnalyzerBridgeServer.startServerLazily(context))
      .isInstanceOf(ServerAlreadyFailedException.class);
  }

  @Test
  public void should_fail_if_bad_json_response() throws Exception {
    cssAnalyzerBridgeServer = createCssAnalyzerBridgeServer(START_SERVER_SCRIPT);
    cssAnalyzerBridgeServer.deploy(context.fileSystem().workDir());
    cssAnalyzerBridgeServer.startServerLazily(context);

    DefaultInputFile inputFile = TestInputFileBuilder.create("foo", "invalid-json-response.css")
      .build();
    Request request = new Request(inputFile.absolutePath(), CONFIG_FILE);
    assertThatThrownBy(() -> cssAnalyzerBridgeServer.analyze(request)).isInstanceOf(IllegalStateException.class);
    assertThat(context.allIssues()).isEmpty();
  }


  public static CssAnalyzerBridgeServer createCssAnalyzerBridgeServer(String startServerScript) {
    CssAnalyzerBridgeServer server = new CssAnalyzerBridgeServer(NodeCommand.builder(), TEST_TIMEOUT_SECONDS, new TestBundle(startServerScript));
    server.start();
    return server;
  }

  public static CssAnalyzerBridgeServer createCssAnalyzerBridgeServer() {
    return createCssAnalyzerBridgeServer(START_SERVER_SCRIPT);
  }

  static class TestBundle implements Bundle {

    final String startServerScript;

    TestBundle(String startServerScript) {
      this.startServerScript = startServerScript;
    }

    @Override
    public void deploy(Path deployLocation) {
      // no-op for unit test
    }

    @Override
    public String startServerScript() {
      return "src/test/resources/mock-start-server/" + startServerScript;
    }
  }
}
