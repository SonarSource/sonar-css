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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.utils.log.LogTester;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class TsConfigFileTest {

  @org.junit.Rule
  public LogTester logTester = new LogTester();

  @Test
  public void test() {
    List<String> files = Arrays.asList("dir1/file1.ts", "dir2/file2.ts", "dir3/file3.ts");
    List<InputFile> inputFiles = files.stream().map(f -> TestInputFileBuilder.create("foo", f).build()).collect(Collectors.toList());

    List<TsConfigFile> tsConfigFiles = Arrays.asList(
      new TsConfigFile("dir1/tsconfig.json", singletonList("foo/dir1/file1.ts")),
      new TsConfigFile("dir2/tsconfig.json", singletonList("foo/dir2/file2.ts")),
      new TsConfigFile("dir3/tsconfig.json", singletonList("foo/dir3/file3.ts"))
    );

    Map<TsConfigFile, List<InputFile>> result = TsConfigFile.inputFilesByTsConfig(tsConfigFiles, inputFiles);
    Assertions.assertThat(result).containsExactly(
      entry(tsConfigFiles.get(0), singletonList(inputFiles.get(0))),
      entry(tsConfigFiles.get(1), singletonList(inputFiles.get(1))),
      entry(tsConfigFiles.get(2), singletonList(inputFiles.get(2)))
    );
  }

  @Test
  public void failsToLoad() {
    List<TsConfigFile> tsConfigFiles = singletonList(new TsConfigFile("tsconfig/path", emptyList()));
    Map<TsConfigFile, List<InputFile>> result = TsConfigFile.inputFilesByTsConfig(tsConfigFiles, emptyList());
    Assertions.assertThat(result).isEmpty();
  }
}
