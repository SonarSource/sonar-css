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

import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import javax.script.ScriptException;

import java.io.IOException;
import java.util.Optional;

public class MetricSensor implements Sensor {

  private static final Logger LOG = Loggers.get(MetricSensor.class);

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor
      .onlyOnLanguage(CssLanguage.KEY);
  }

  @Override
  public void execute(SensorContext context) {
    FileSystem fileSystem = context.fileSystem();
    Iterable<InputFile> inputFiles = fileSystem.inputFiles(fileSystem.predicates().hasLanguage(CssLanguage.KEY));

    Tokenizer tokenizer = new Tokenizer();

    for (InputFile input : inputFiles) {
      saveHighlights(context, input, tokenizer);
    }
  }

  private static void saveHighlights(SensorContext sensorContext, InputFile input, Tokenizer tokenizer) {
    try {
      NewHighlighting highlighting = sensorContext.newHighlighting().onFile(input);
      tokenizer.tokenize(input.contents())
        .forEach(token -> getHighlightingType(token).ifPresent(type ->
          highlighting.highlight(token.startLine, token.startColumn, token.endLine, token.endColumn, type)));
      highlighting.save();

    } catch (ScriptException e) {
      LOG.error(String.format("Failed to tokenize file '%s'", input.toString()), e);
    } catch (IOException e) {
      LOG.error(String.format("Failed to read file '%s'", input.toString()), e);
    }
  }

  private static Optional<TypeOfText> getHighlightingType(Token token) {
    switch (token.type) {
      case COMMENT:
        return Optional.of(TypeOfText.COMMENT);

      case STRING:
        return Optional.of(TypeOfText.STRING);

      default:
        return Optional.empty();
    }
  }
}
