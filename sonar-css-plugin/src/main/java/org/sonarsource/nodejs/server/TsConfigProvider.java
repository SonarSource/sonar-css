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
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonar.api.SonarProduct;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

class TsConfigProvider {

  private static final Logger LOG = Loggers.get(TsConfigProvider.class);

  interface Provider {
    List<String> tsconfigs(SensorContext context) throws IOException;
  }

  private final List<Provider> providers;

  TsConfigProvider(TempFolder folder) {
    providers = Arrays.asList(
      new PropertyTsConfigProvider(),
      new LookupTsConfigProvider(),
      new DefaultTsConfigProvider(folder));
  }

  List<String> tsconfigs(SensorContext context) throws IOException {
    for (Provider provider : providers) {
      List<String> tsconfigs = provider.tsconfigs(context);
      if (!tsconfigs.isEmpty()) {
        return tsconfigs;
      }
    }
    return emptyList();
  }

  static class PropertyTsConfigProvider implements Provider {

    @Override
    public List<String> tsconfigs(SensorContext context) {
      Optional<String> tsConfigProperty = context.config().get("sonar.typescript.tsconfigPath"); // FIXME JavaScriptPlugin.TSCONFIG_PATH
      if (tsConfigProperty.isPresent()) {
        Path tsconfig = Paths.get(tsConfigProperty.get());
        tsconfig = tsconfig.isAbsolute() ? tsconfig : context.fileSystem().baseDir().toPath().resolve(tsconfig);
        if (!tsconfig.toFile().exists()) {
          String msg = format("Provided tsconfig.json path doesn't exist. Path: '%s'", tsconfig);
          LOG.error(msg);
          throw new IllegalStateException(msg);
        }
        LOG.info("Using {} from {} property", tsconfig, "sonar.typescript.tsconfigPath");  // FIXME JavaScriptPlugin.TSCONFIG_PATH
        return singletonList(tsconfig.toString());
      }
      return emptyList();
    }
  }

  static class LookupTsConfigProvider implements Provider {

    @Override
    public List<String> tsconfigs(SensorContext context) throws IOException {
      FileSystem fs = context.fileSystem();
      Path baseDir = fs.baseDir().toPath();
      try (Stream<Path> files = Files.walk(baseDir)) {
        List<String> tsconfigs = files
          .filter(p -> p.endsWith("tsconfig.json") && !isNodeModulesPath(p))
          .map(p -> p.toAbsolutePath().toString())
          .collect(Collectors.toList());
        LOG.info("Found " + tsconfigs.size() + " tsconfig.json file(s): " + tsconfigs);
        return tsconfigs;
      }
    }

    private static boolean isNodeModulesPath(Path p) {
      Path nodeModules = Paths.get("node_modules");
      return StreamSupport.stream(p.spliterator(), false).anyMatch(nodeModules::equals);
    }
  }

  static class DefaultTsConfigProvider implements Provider {

    private final TempFolder folder;

    DefaultTsConfigProvider(TempFolder folder) {
      this.folder = folder;
    }

    @Override
    public List<String> tsconfigs(SensorContext context) throws IOException {
      if (context.runtime().getProduct() == SonarProduct.SONARLINT) {
        // we don't support per analysis temporary files in SonarLint see https://jira.sonarsource.com/browse/SLCORE-235
        LOG.warn("Generating temporary tsconfig is not supported in SonarLint context.");
        return emptyList();
      }
      Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(TypeScriptSensor.filePredicate(context.fileSystem()));
      TsConfig tsConfig = new TsConfig(inputFiles);
      File tsconfigFile = writeToJsonFile(tsConfig);
      LOG.info("Using generated tsconfig.json file {}", tsconfigFile.getAbsolutePath());
      return singletonList(tsconfigFile.getAbsolutePath());
    }

    private File writeToJsonFile(TsConfig tsConfig) throws IOException {
      String json = new Gson().toJson(tsConfig);
      File tsconfigFile = folder.newFile();
      Files.write(tsconfigFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
      return tsconfigFile;
    }

    private static class TsConfig {
      List<String> files;

      TsConfig(Iterable<InputFile> inputFiles) {
        files = new ArrayList<>();
        inputFiles.forEach(f -> files.add(f.absolutePath()));
      }
    }
  }
}
