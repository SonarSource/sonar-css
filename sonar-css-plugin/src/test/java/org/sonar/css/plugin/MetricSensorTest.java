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
package org.sonar.css.plugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.DefaultSensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.FileLinesContext;
import org.sonar.api.measures.FileLinesContextFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricSensorTest {

  private DefaultInputFile inputFile;
  private SensorContextTester sensorContext;

  @Rule
  public TemporaryFolder tempFolder = new TemporaryFolder();

  @Test
  public void should_describe() {
    DefaultSensorDescriptor desc = new DefaultSensorDescriptor();
    new MetricSensor(null).describe(desc);

    assertThat(desc.languages()).containsOnly("css");
  }

  @Test
  public void empty_input() throws Exception {
    executeSensor("foo");
    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 1, 0)).isEmpty();
    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 1, 1)).isEmpty();
  }

  @Test
  public void comment() throws IOException {
    executeSensor("/* some comment */");
    assertHighlighting(1, 0, 18, TypeOfText.COMMENT);

    executeSensor("/* some comment\nmultiline */");
    assertHighlighting(1, 0, 15, TypeOfText.COMMENT);
    assertHighlighting(2, 0, 12, TypeOfText.COMMENT);
  }

  @Test
  public void string() throws IOException {
    executeSensor("\"foo\"");
    assertHighlighting(1, 0, 5, TypeOfText.STRING);

    executeSensor("\"foo\\\nbar\"");
    assertHighlighting(1, 0, 4, TypeOfText.STRING);
    assertHighlighting(2, 0, 4, TypeOfText.STRING);
  }

  @Test
  public void constant() throws IOException {
    executeSensor("1");
    assertHighlighting(1, 0, 1, TypeOfText.CONSTANT);

    executeSensor("1.0");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    executeSensor("0px");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    executeSensor("1em");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    executeSensor("#ddd");
    assertHighlighting(1, 0, 4, TypeOfText.CONSTANT);
  }

  @Test
  public void annotation() throws IOException {
    executeSensor("@bar { }");
    assertHighlighting(1, 0, 4, TypeOfText.ANNOTATION);

    executeSensor("@my-selector: banner;");
    assertHighlighting(1, 0, 12, TypeOfText.ANNOTATION);

    executeSensor("@import \"src/themes\"");
    assertHighlighting(1, 0, 7, TypeOfText.ANNOTATION);

    executeSensor(".element { color: @@color }");
    assertHighlighting(1, 18, 7, TypeOfText.ANNOTATION);
  }

  @Test
  public void keyword() throws IOException {
    executeSensor("$foo { }");
    assertHighlighting(1, 0, 4, TypeOfText.KEYWORD);

    executeSensor("#header { .border-radius(4px); }");
    assertHighlighting(1, 0, 7, TypeOfText.KEYWORD);
  }

  @Test
  public void keyword_light() throws IOException {
    executeSensor("bar: foo { }");
    assertHighlighting(1, 0, 3, TypeOfText.KEYWORD_LIGHT);

    executeSensor("bar { foo: 1px }");
    assertHighlighting(1, 6, 3, TypeOfText.KEYWORD_LIGHT);

    executeSensor("bar { foo-bar: 1px }");
    assertHighlighting(1, 6, 7, TypeOfText.KEYWORD_LIGHT);
  }

  @Test
  public void lines_of_code() throws IOException {
    executeSensor("bar { }");
    assertLinesOfCode(1);

    executeSensor("bar\n{ }");
    assertLinesOfCode(2);

    // We don't count empty lines
    executeSensor("\n\n\nsomething\n\n\n");
    assertLinesOfCode(1);

    // We don't count comments
    executeSensor("// foo");
    assertLinesOfCode(0);
    executeSensor("/* dasdsa */");
    assertLinesOfCode(0);
    executeSensor("/* das\ndsa */");
    assertLinesOfCode(0);

    // Mix code and comment
    executeSensor("foo {} // some comment");
    assertLinesOfCode(1);
  }

  @Test
  public void lines_of_comment() throws IOException {
    executeSensor("// inline comment");
    assertLinesOfComment(1);

    executeSensor("/* single line comment */");
    assertLinesOfComment(1);

    executeSensor("/* multiline\n *\n *\n * comment\n*/");
    assertLinesOfComment(5);

    // We don't count empty lines
    executeSensor("\n\n\n/* something */\n\n\n");
    assertLinesOfComment(1);

    // We don't count code
    executeSensor("foo {}");
    assertLinesOfComment(0);

    // Mix code and comment
    executeSensor("foo {} // some comment");
    assertLinesOfComment(1);
  }

  private void executeSensor(String content) throws IOException {
    File file = tempFolder.newFile();
    inputFile = new TestInputFileBuilder("moduleKey", file.getName())
      .setLanguage("css")
      .setContents(content)
      .build();

    sensorContext = SensorContextTester.create(tempFolder.getRoot());
    sensorContext.fileSystem().add(inputFile);

    FileLinesContext linesContext = mock(FileLinesContext.class);
    FileLinesContextFactory linesContextFactory = mock(FileLinesContextFactory.class);
    when(linesContextFactory.createFor(inputFile)).thenReturn(linesContext);
    new MetricSensor(linesContextFactory).execute(sensorContext);
  }

  private void assertHighlighting(int line, int column, int length, TypeOfText type) {
    for (int i = column; i < column + length; i++) {
      List<TypeOfText> typeOfTexts = sensorContext.highlightingTypeAt(inputFile.key(), line, i);
      assertThat(typeOfTexts).containsOnly(type);
    }
  }

  private void assertLinesOfCode(int expected) {
    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.NCLOC).value()).isEqualTo(expected);
  }

  private void assertLinesOfComment(int expected) {
    assertThat(sensorContext.measure(inputFile.key(), CoreMetrics.COMMENT_LINES).value()).isEqualTo(expected);
  }
}
