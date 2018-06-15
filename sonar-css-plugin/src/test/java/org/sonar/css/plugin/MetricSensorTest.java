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
    assertThat(sensorContext.highlightingTypeAt(inputFile.key(), 1, 1)).isEmpty();
  }

  @Test
  public void comment() throws IOException {
    highlight("/* some comment */");
    assertHighlighting(1, 0, 18, TypeOfText.COMMENT);

    highlight("/* some comment\nmultiline */");
    assertHighlighting(1, 0, 15, TypeOfText.COMMENT);
    assertHighlighting(2, 0, 12, TypeOfText.COMMENT);
  }

  @Test
  public void string() throws IOException {
    highlight("\"foo\"");
    assertHighlighting(1, 0, 5, TypeOfText.STRING);

    highlight("\"foo\nbar\"");
    assertHighlighting(1, 0, 4, TypeOfText.STRING);
    assertHighlighting(2, 0, 4, TypeOfText.STRING);
  }

  @Test
  public void constant() throws IOException {
    highlight("1");
    assertHighlighting(1, 0, 1, TypeOfText.CONSTANT);

    highlight("1.0");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    highlight("0px");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    highlight("1em");
    assertHighlighting(1, 0, 3, TypeOfText.CONSTANT);

    highlight("#ddd");
    assertHighlighting(1, 0, 4, TypeOfText.CONSTANT);
  }

  @Test
  public void annotation() throws IOException {
    highlight("@bar { }");
    assertHighlighting(1, 0, 4, TypeOfText.ANNOTATION);

    highlight("@my-selector: banner;");
    assertHighlighting(1, 0, 12, TypeOfText.ANNOTATION);

    highlight("@import \"src/themes\"");
    assertHighlighting(1, 0, 7, TypeOfText.ANNOTATION);

    highlight(".element { color: @@color }");
    assertHighlighting(1, 18, 7, TypeOfText.ANNOTATION);
  }

  @Test
  public void keyword() throws IOException {
    highlight("foo { }");
    assertHighlighting(1, 0, 3, TypeOfText.KEYWORD);

    highlight(".foo { }");
    assertHighlighting(1, 0, 4, TypeOfText.KEYWORD);

    highlight(".foo bar { }");
    assertHighlighting(1, 0, 4, TypeOfText.KEYWORD);
    assertHighlighting(1, 5, 3, TypeOfText.KEYWORD);

    highlight(".border-radius(@radius) { }");
    assertHighlighting(1, 0, 14, TypeOfText.KEYWORD);

    highlight("#header { .border-radius(4px); }");
    assertHighlighting(1, 0, 7, TypeOfText.KEYWORD);
    assertHighlighting(1, 10, 14, TypeOfText.KEYWORD);
  }

  @Test
  public void keyword_light() throws IOException {
    highlight("bar: foo { }");
    assertHighlighting(1, 0, 3, TypeOfText.KEYWORD_LIGHT);

    highlight("bar { foo: 1px }");
    assertHighlighting(1, 6, 3, TypeOfText.KEYWORD_LIGHT);

    highlight("bar { foo-bar: 1px }");
    assertHighlighting(1, 6, 7, TypeOfText.KEYWORD_LIGHT);
  }

  private void highlight(String content) throws IOException {
    File file = tempFolder.newFile();
    inputFile = new TestInputFileBuilder("moduleKey", file.getName())
      .setLanguage("css")
      .setContents(content)
      .build();

    sensorContext = SensorContextTester.create(tempFolder.getRoot());
    sensorContext.fileSystem().add(inputFile);

    new MetricSensor().execute(sensorContext);
  }

  private void assertHighlighting(int line, int column, int length, TypeOfText type) {
    for (int i = column; i < column + length; i++) {
      List<TypeOfText> typeOfTexts = sensorContext.highlightingTypeAt(inputFile.key(), line, i);
      assertThat(typeOfTexts).containsOnly(type);
    }
  }
}
