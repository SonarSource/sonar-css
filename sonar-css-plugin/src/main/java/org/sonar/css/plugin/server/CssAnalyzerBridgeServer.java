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
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.css.plugin.bundle.Bundle;
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
  final int timeoutSeconds;
  private final Bundle bundle;
  private int port;
  private NodeCommand nodeCommand;
  private boolean failedToStart;

  // Used by pico container for dependency injection
  @SuppressWarnings("unused")
  public CssAnalyzerBridgeServer(Bundle bundle) {
    this(NodeCommand.builder(), DEFAULT_TIMEOUT_SECONDS, bundle);
  }

  protected CssAnalyzerBridgeServer(NodeCommandBuilder nodeCommandBuilder, int timeoutSeconds, Bundle bundle) {
    this.nodeCommandBuilder = nodeCommandBuilder;
    this.timeoutSeconds = timeoutSeconds;
    this.bundle = bundle;
    this.client = new OkHttpClient.Builder()
      .callTimeout(Duration.ofSeconds(timeoutSeconds))
      .readTimeout(Duration.ofSeconds(timeoutSeconds))
      .build();
  }

  // for testing purposes
  public void deploy(File deployLocation) throws IOException {
    bundle.deploy(deployLocation.toPath());
  }

  public void startServer(SensorContext context) throws IOException {
    PROFILER.startDebug("Starting server");
    port = NetUtils.findOpenPort();

    File scriptFile = new File(bundle.startServerScript());
    if (!scriptFile.exists()) {
      throw new NodeCommandException("Node.js script to start css-bundle server doesn't exist: " + scriptFile.getAbsolutePath());
    }

    initNodeCommand(context, scriptFile);

    LOG.debug("Starting Node.js process to start css-bundle server at port " + port);
    nodeCommand.start();

    if (!NetUtils.waitServerToStart("localhost", port, timeoutSeconds * 1000)) {
      throw new NodeCommandException("Failed to start server (" + timeoutSeconds + "s timeout)");
    }
    PROFILER.stopDebug();
  }

  private void initNodeCommand(SensorContext context, File scriptFile) {
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
  public void startServerLazily(SensorContext context) throws IOException {
    // required for SonarLint context to avoid restarting already failed server
    if (failedToStart) {
      throw new ServerAlreadyFailedException();
    }

    try {
      if (isAlive()) {
        LOG.debug("css-bundle server is up, no need to start.");
        return;
      }
      deploy(context.fileSystem().workDir());
      startServer(context);
    } catch (NodeCommandException e) {
      failedToStart = true;
      throw e;
    }
  }

  @Override
  public Issue[] analyze(Request request) throws IOException {
    String json = GSON.toJson(request);
    return response(request(json));
  }

  private String request(String json) throws IOException {
    okhttp3.Request request = new okhttp3.Request.Builder()
      .url(url("analyze"))
      .post(RequestBody.create(MediaType.get("application/json"), json))
      .build();

    try (Response response = client.newCall(request).execute()) {
      // in this case response.body() is never null (according to docs)
      return response.body().string();
    }
  }

  private static Issue[] response(String result) {
    try {
      return GSON.fromJson(result, Issue[].class);
    } catch (JsonSyntaxException e) {
      String msg = "Failed to parse response: \n-----\n" + result + "\n-----\n";
      LOG.error(msg, e);
      throw new IllegalStateException("Failed to parse response", e);
    }
  }

  public boolean isAlive() {
    if (nodeCommand == null) {
      return false;
    }
    okhttp3.Request request = new okhttp3.Request.Builder()
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
      return "Node.js command to start css-bundle server was not built yet.";
    } else {
      return "Node.js command to start css-bundle was: " + nodeCommand.toString();
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

  // for testing purposes
  public void setPort(int port) {
    this.port = port;
  }

}
