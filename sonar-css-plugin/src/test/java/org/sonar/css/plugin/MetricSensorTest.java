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

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import static org.assertj.core.api.Assertions.assertThat;

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
    highlight("foo");

    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 1, 0)).isEmpty();
  }

  @Test
  public void singleline_multiline_comment() throws IOException {
    highlight("/* some comment */");

    assertHighlighting(1, 1, 17, TypeOfText.COMMENT);
  }

  @Test
  public void multiline_comment() throws IOException {
    String content = "/* some comment\nmultiline */";
    highlight(content);

    assertHighlighting(1, 1, 15, TypeOfText.COMMENT);
    assertHighlighting(1, 1, 12, TypeOfText.COMMENT);
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
    if (column < 1) {
      throw new IllegalStateException("Column should be greater than or equal to 1");
    }

    for (int i = column; i < column + length; i++) {
      List<TypeOfText> typeOfTexts = sensorContext.highlightingTypeAt(inputFile.key(), line, i);
      assertThat(typeOfTexts).containsOnly(type);
    }
  }
}
