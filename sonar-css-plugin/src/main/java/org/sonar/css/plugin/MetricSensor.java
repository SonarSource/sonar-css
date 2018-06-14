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

import java.io.IOException;
import java.util.List;
import javax.script.ScriptException;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

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
      List<Token> tokenList = tokenizer.tokenize(input.contents());

      for (int i = 0; i < tokenList.size(); i++) {
        Token currentToken = tokenList.get(i);
        Token nextToken = i + 1 == tokenList.size() ? null : tokenList.get(i + 1);

        TypeOfText highlightingType = null;
        switch (currentToken.type) {
          case COMMENT:
            highlightingType = TypeOfText.COMMENT;
            break;

          case STRING:
            highlightingType = TypeOfText.STRING;
            break;

          case WORD:
            if (Character.isDigit(currentToken.text.charAt(0)) || currentToken.text.matches("^#[0-9a-fA-F]+$")) {
              highlightingType = TypeOfText.CONSTANT;
            } else if (nextToken != null && nextToken.text.equals(":")) {
              highlightingType = TypeOfText.KEYWORD_LIGHT;
            } else if (currentToken.text.startsWith(".") || (nextToken != null && nextToken.text.startsWith("{"))) {
              highlightingType = TypeOfText.KEYWORD;
            }
            break;

          case AT_WORD:
            highlightingType = TypeOfText.ANNOTATION;
            break;

          default:
            highlightingType = null;
        }

        if (highlightingType != null) {
          highlighting.highlight(currentToken.startLine, currentToken.startColumn - 1, currentToken.endLine, currentToken.endColumn, highlightingType);
        }
      }

      highlighting.save();

    } catch (ScriptException e) {
      LOG.error(String.format("Failed to tokenize file '%s'", input.toString()), e);
    } catch (IOException e) {
      LOG.error(String.format("Failed to read file '%s'", input.toString()), e);
    }
  }

}
