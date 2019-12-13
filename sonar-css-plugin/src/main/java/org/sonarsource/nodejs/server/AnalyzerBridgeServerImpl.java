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
package org.sonarsource.nodejs.server;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonarsource.nodejs.NodeCommand;
import org.sonarsource.nodejs.NodeCommandBuilder;
import org.sonarsource.nodejs.NodeCommandException;

import static java.util.Collections.emptyList;

public class AnalyzerBridgeServerImpl implements AnalyzerBridgeServer {

  private static final Logger LOG = Loggers.get(AnalyzerBridgeServerImpl.class);
  private static final Profiler PROFILER = Profiler.createIfDebug(LOG);

  // SonarLint should pass in this property an absolute path to the directory containing TypeScript dependency
  private static final String TYPESCRIPT_DEPENDENCY_LOCATION_PROPERTY = "sonar.typescript.internal.typescriptLocation";

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
  private Configuration configuration;

  // Used by pico container for dependency injection
  @SuppressWarnings("unused")
  public AnalyzerBridgeServerImpl(Configuration configuration, NodeCommandBuilder nodeCommandBuilder, Bundle bundle) {
    this(configuration, nodeCommandBuilder, DEFAULT_TIMEOUT_SECONDS, bundle);
  }

  protected AnalyzerBridgeServerImpl(Configuration configuration, NodeCommandBuilder nodeCommandBuilder, int timeoutSeconds,
                         Bundle bundle) {
    this.configuration = configuration;
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

    if (shouldDetectTypeScript(context.fileSystem())) {
      Optional<Path> typeScriptLocation = getTypeScriptLocation(context.fileSystem().baseDir());
      if (typeScriptLocation.isPresent()) {
        LOG.info("Using TypeScript at: '{}'", typeScriptLocation.get());
        nodeCommandBuilder.addToNodePath(typeScriptLocation.get().toAbsolutePath());
      } else {
        LOG.info("TypeScript dependency was not found inside project directory, Node.js will search TypeScript using " +
          "module resolution algorithm; analysis will fail without TypeScript.");
      }
    }
    nodeCommand = nodeCommandBuilder.build();
  }

  private static boolean shouldDetectTypeScript(FileSystem fileSystem) {
    return fileSystem.hasFiles(TypeScriptSensor.filePredicate(fileSystem));
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

  @Override
  public AnalysisResponse analyze(String endpoint, AnalysisRequest request) throws IOException {
    String json = GSON.toJson(request);
    return response(request(json, endpoint), request.filePath);
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

  private static AnalysisResponse response(String result, String filePath) {
    try {
      return GSON.fromJson(result, AnalysisResponse.class);
    } catch (JsonSyntaxException e) {
      String msg = "Failed to parse response for file " + filePath + ": \n-----\n" + result + "\n-----\n";
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
  public boolean newTsConfig() {
    Request request = new Request.Builder()
      .url(url("new-tsconfig"))
      .post(RequestBody.create(null, ""))
      .build();
    try (Response response = client.newCall(request).execute()) {
      String body = response.body().string();
      return "OK!".equals(body);
    } catch (IOException e) {
      LOG.error("Failed to post new-tsconfig", e);
    }
    return false;
  }

  public TsConfigResponse tsConfigFiles(String tsconfigAbsolutePath) {
    String result = null;
    try {
      TsConfigRequest tsConfigRequest = new TsConfigRequest(tsconfigAbsolutePath);
      result = request(GSON.toJson(tsConfigRequest), "tsconfig-files");
      return GSON.fromJson(result, TsConfigResponse.class);
    } catch (IOException e) {
      LOG.error("Failed to request files for tsconfig: " + tsconfigAbsolutePath, e);
    } catch (JsonSyntaxException e) {
      LOG.error("Failed to parse response when requesting files for tsconfig: " + tsconfigAbsolutePath + ": \n-----\n" + result + "\n-----\n");
    }
    return new TsConfigResponse(emptyList(), result, null);
  }

  @Override
  public TsConfigFile loadTsConfig(String filename) {
    AnalyzerBridgeServer.TsConfigResponse tsConfigResponse = tsConfigFiles(filename);
    if (tsConfigResponse.error != null) {
      LOG.error(tsConfigResponse.error);
      if (tsConfigResponse.errorCode == AnalyzerBridgeServer.ParsingErrorCode.MISSING_TYPESCRIPT) {
        TypeScriptSensor.logMissingTypescript();
        throw new MissingTypeScriptException();
      }
    }
    return new TsConfigFile(filename, tsConfigResponse.files == null ? emptyList() : tsConfigResponse.files);
  }

  @Override
  public void clean() {
    if (nodeCommand != null) {
      nodeCommand.destroy();
      nodeCommand = null;
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

  private HttpUrl url(String endpoint) {
    HttpUrl.Builder builder = new HttpUrl.Builder();
    return builder
      .scheme("http")
      .host("localhost")
      .port(port)
      .addPathSegment(endpoint)
      .build();
  }

  private Optional<Path> getTypeScriptLocation(File baseDir) throws IOException {
    // we have to use global Configuration and not SensorContext#config to lookup typescript set from vscode extension
    // see https://jira.sonarsource.com/browse/SLCORE-250
    Optional<String> typeScriptLocationProperty = configuration.get(TYPESCRIPT_DEPENDENCY_LOCATION_PROPERTY);
    if (typeScriptLocationProperty.isPresent()) {
      LOG.debug("TypeScript location set via property {}={}", TYPESCRIPT_DEPENDENCY_LOCATION_PROPERTY, typeScriptLocationProperty.get());
      return Optional.of(Paths.get(typeScriptLocationProperty.get()));
    }
    LOG.debug("Looking for TypeScript recursively in {}", baseDir.getAbsolutePath());
    try (Stream<Path> files = Files.walk(baseDir.toPath())) {
      return files
        .filter(p -> p.toFile().isDirectory() && p.endsWith("node_modules/typescript"))
        .findFirst()
        .map(Path::getParent);
    }
  }

  static class TsConfigRequest {
    final String tsconfig;

    TsConfigRequest(String tsconfig) {
      this.tsconfig = tsconfig;
    }
  }
}
