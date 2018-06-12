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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.sonar.api.internal.apachecommons.lang.StringEscapeUtils;
import org.sonar.css.plugin.Token.Type;

public class Tokenizer {

  public List<Token> tokenize(String css) throws ScriptException {
    ScriptEngineManager factory = new ScriptEngineManager();
    ScriptEngine engine = factory.getEngineByName("JavaScript");
    InputStream tokenizeScript = Tokenizer.class.getClassLoader().getResourceAsStream("tokenize.js");
    engine.eval(new InputStreamReader(tokenizeScript, StandardCharsets.UTF_8));
    String cssInput = "tokenize('" + StringEscapeUtils.escapeJavaScript(css) + "')";
    Object tokens = engine.eval(cssInput);
    return extractTokens(tokens);
  }

  private static List<Token> extractTokens(Object tokens) {
    // We receive an array of array of tokens from the JavaScript Tokenizer. When casted to java object the array is
    // mapped as a map where the key is the index and the value is the actual value of the array for the given index.
    // Keys are useless as we iterate through values (the array).

    List<Token> resultList = new ArrayList<>();
    for (Object tokenObject : ((Map<String, Object>) tokens).values()) {

      // Access the inner arrays (disregard the keys) and use their length to decide which type of token we are
      // dealing with.
      Map<String, Object> tokenProperties = (Map<String, Object>) tokenObject;

      // skip whitespace token (size < 4)
      if (tokenProperties.size() >= 4) {
        String text = tokenProperties.get("1").toString();
        Type type = computeType(tokenProperties.get("0").toString(), text);
        Integer startLine = convertToInt(tokenProperties.get("2"));
        Integer startColumn = ((Double) tokenProperties.get("3")).intValue();

        // all cases except for punctuator type
        if (tokenProperties.size() == 6) {
          Integer endLine = convertToInt(tokenProperties.get("4"));
          Integer endColumn = ((Double) tokenProperties.get("5")).intValue();

          // For the sake of simplicity we don't handle words ending with ',' on a new line
          if (text.length() > 1 && text.endsWith(",") && startLine.equals(endLine)) {
            resultList.add(new Token(type, text.substring(0, text.length() - 1), startLine, startColumn, endLine, endColumn - 1));
            resultList.add(new Token(Type.PUNCTUATOR, ",", startLine, endColumn, endLine, endColumn));
          } else {
            resultList.add(new Token(type, text, startLine, startColumn, endLine, endColumn));
          }
        } else {
          // is punctuator
          resultList.add(new Token(type, text, startLine, startColumn, startLine, startColumn));
        }
      }
    }

    return resultList;
  }

  private static Integer convertToInt(Object value) {
    if (value instanceof Double) {
      return ((Double) value).intValue();
    } else if (value instanceof Integer) {
      return  (Integer) value;
    } else {
      throw new IllegalStateException("Failed to convert to number: " + value);
    }
  }

  private static Type computeType(String type, String text) {
    switch (type) {
      case "at-word":
        return Type.AT_WORD;
      case "word":
        if (",".equals(text)) {
          return Type.PUNCTUATOR;
        } else {
          return Type.WORD;
        }
      case "comment":
        return Type.COMMENT;
      case "string":
        return Type.STRING;
      case "brackets":
        return Type.BRACKETS;
      default:
        return Type.PUNCTUATOR;
    }
  }
}
