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

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.css.plugin.server.exception.ServerAlreadyFailedException;
import org.sonarsource.nodejs.NodeCommand;
import org.sonarsource.nodejs.NodeCommandBuilder;
import org.sonarsource.nodejs.NodeCommandException;

public class CssAnalyzerBridgeServer implements AnalyzerBridgeServer {

  private static final Logger LOG = Loggers.get(CssAnalyzerBridgeServer.class);
  private static final Profiler PROFILER = Profiler.createIfDebug(LOG);

  private static final int DEFAULT_TIMEOUT_SECONDS = 60;
  // internal property to set "--max-old-space-size" for Node process running this server
  private static final String MAX_OLD_SPACE_SIZE_PROPERTY = "sonar.javascript.node.maxspace";
  private static final Gson GSON = new Gson();

  private final OkHttpClient client;
  private final NodeCommandBuilder nodeCommandBuilder;
  private final int timeoutSeconds;
  private final Bundle bundle;
  private int port;
  private NodeCommand nodeCommand;
  private boolean failedToStart;

  // Used by pico container for dependency injection
  @SuppressWarnings("unused")
  public CssAnalyzerBridgeServer(NodeCommandBuilder nodeCommandBuilder, Bundle bundle) {
    this(nodeCommandBuilder, DEFAULT_TIMEOUT_SECONDS, bundle);
  }

  protected CssAnalyzerBridgeServer(NodeCommandBuilder nodeCommandBuilder, int timeoutSeconds,
                         Bundle bundle) {
    this.nodeCommandBuilder = nodeCommandBuilder;
    this.timeoutSeconds = timeoutSeconds;
    this.bundle = bundle;
    this.client = new OkHttpClient.Builder()
      .callTimeout(Duration.ofSeconds(timeoutSeconds))
      .readTimeout(Duration.ofSeconds(timeoutSeconds))
      .build();
  }

  public void deploy() throws IOException {
    bundle.deploy();
  }

  public void startServer(SensorContext context) throws IOException, NodeCommandException {
    PROFILER.startDebug("Starting server");
    port = NetUtils.findOpenPort();

    File scriptFile = new File(bundle.startServerScript());
    if (!scriptFile.exists()) {
      throw new NodeCommandException("Node.js script to start " + bundle.name() + " server doesn't exist: " + scriptFile.getAbsolutePath());
    }

    initNodeCommand(context, scriptFile);

    LOG.debug("Starting Node.js process to start " + bundle.name() + " server at port " + port);
    nodeCommand.start();

    if (!NetUtils.waitServerToStart("localhost", port, timeoutSeconds * 1000)) {
      throw new NodeCommandException("Failed to start server (" + timeoutSeconds + "s timeout)");
    }
    PROFILER.stopDebug();
  }

  private void initNodeCommand(SensorContext context, File scriptFile) throws IOException {
    nodeCommandBuilder
      .outputConsumer(message -> {
        if (message.startsWith("DEBUG")) {
          LOG.debug(message.substring(5).trim());
        } else if (message.startsWith("WARN")) {
          LOG.warn(message.substring(4).trim());
        } else {
          LOG.info(message);
        }
      })
      .pathResolver(bundle)
      .minNodeVersion(8)
      .configuration(context.config())
      .script(scriptFile.getAbsolutePath())
      .scriptArgs(String.valueOf(port));

    context.config()
      .getInt(MAX_OLD_SPACE_SIZE_PROPERTY)
      .ifPresent(nodeCommandBuilder::maxOldSpaceSize);

    nodeCommand = nodeCommandBuilder.build();
  }

  @Override
  public void startServerLazily(SensorContext context) throws IOException, ServerAlreadyFailedException, NodeCommandException {
    // required for SonarLint context to avoid restarting already failed server
    if (failedToStart) {
      throw new ServerAlreadyFailedException();
    }

    try {
      if (isAlive()) {
        LOG.debug(bundle.name() + " server is up, no need to start.");
        return;
      }
      deploy();
      startServer(context);
    } catch (NodeCommandException e) {
      failedToStart = true;
      throw e;
    }
  }

  public AnalysisResponse analyze(AnalysisRequest request) throws IOException {
    return analyze("analyze-css", request, AnalysisResponse.class);
  }

  @Override
  public <REQ, RES> RES analyze(String endpoint, REQ request, Class<RES> cls) throws IOException {
    String json = GSON.toJson(request);
    return response(request(json, endpoint), cls);
  }

  private String request(String json, String endpoint) throws IOException {
    Request request = new Request.Builder()
      .url(url(endpoint))
      .post(RequestBody.create(MediaType.get("application/json"), json))
      .build();

    try (Response response = client.newCall(request).execute()) {
      // in this case response.body() is never null (according to docs)
      return response.body().string();
    }
  }

  private static <RES> RES response(String result, Class<RES> cls) {
    try {
      return GSON.fromJson(result, cls);
    } catch (JsonSyntaxException e) {
      String msg = "Failed to parse response for file " + /*TODO*/ ": \n-----\n" + result + "\n-----\n";
      LOG.error(msg, e);
      throw new IllegalStateException("Failed to parse response", e);
    }
  }


  public boolean isAlive() {
    if (nodeCommand == null) {
      return false;
    }
    Request request = new Request.Builder()
      .url(url("status"))
      .get()
      .build();

    try (Response response = client.newCall(request).execute()) {
      String body = response.body().string();
      // in this case response.body() is never null (according to docs)
      return "OK!".equals(body);
    } catch (IOException e) {
      LOG.error("Error requesting server status. Server is probably dead.", e);
      return false;
    }
  }

  @Override
  public String getCommandInfo() {
    if (nodeCommand == null) {
      return "Node.js command to start " + bundle.name() + " server was not built yet.";
    } else {
      return "Node.js command to start " + bundle.name() + " was: " + nodeCommand.toString();
    }
  }

  @Override
  public void start() {
    // Server is started lazily by the sensor
  }

  @Override
  public void stop() {
    clean();
  }

  void clean() {
    if (nodeCommand != null) {
      nodeCommand.destroy();
      nodeCommand = null;
    }
  }

  private HttpUrl url(String endpoint) {
    HttpUrl.Builder builder = new HttpUrl.Builder();
    return builder
      .scheme("http")
      .host("localhost")
      .port(port)
      .addPathSegment(endpoint)
      .build();
  }

  public static class AnalysisRequest {
    String filePath;
    @Nullable
    String configFile;

    public AnalysisRequest(String filePath, @Nullable String configFile) {
      this.filePath = filePath;
      this.configFile = configFile;
    }
  }

  public static class AnalysisResponse {
    String[] issues;
  }

}
