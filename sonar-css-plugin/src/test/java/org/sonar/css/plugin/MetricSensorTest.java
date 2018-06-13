/*
 * SonarCSS
 * Copyright (C) 2018-2018 SonarSource SA
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
package org.sonar.css.plugin;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.measures.Metric;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.mockito.Mockito.verify;

public class MetricSensorTest {

  private DefaultInputFile inputFile;
  private SensorContextTester sensorContext;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Before
  public void setUp() {
    sensorContext = SensorContextTester.create(tempFolder.getRoot());
  }

  @Test
  public void should_describe() {
    DefaultSensorDescriptor desc = new DefaultSensorDescriptor();
    new MetricSensor().describe(desc);

    assertThat(desc.languages()).containsOnly("css");
  }

  @Test
  public void empty_input() throws Exception {
    highlight("");

    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 1, 0)).isEmpty();
  }

  @Test
  public void multiline_comment() throws IOException {
    String content = "/* some comment */";
    highlight(content);

    assertHighlighting(1, 1, content.length() - 1, TypeOfText.COMMENT);
  }

  @Test
  public void string() throws IOException {
    String content = "\"foo\"";
    highlight(content);

    assertHighlighting(1, 1, content.length() - 1, TypeOfText.STRING);
  }

  private void highlight(String content) throws IOException {
    File file = tempFolder.newFile();
    inputFile = new TestInputFileBuilder("moduleKey", file.getName())
      .setLanguage("css")
      .setContents(content)
      .build();

    sensorContext.fileSystem().add(inputFile);

    new MetricSensor().execute(sensorContext);
  }

  private void assertHighlighting(int line, int column, int length, TypeOfText type) {
    for (int i = column; i < column + length; i++) {
      List<TypeOfText> typeOfTexts = sensorContext.highlightingTypeAt(inputFile.key(), line, i);
      assertThat(typeOfTexts).hasSize(1);
      assertThat(typeOfTexts.get(0)).isEqualTo(type);
    }
  }
}
