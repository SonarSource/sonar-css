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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.sonar.css.plugin.Token.Type;

public class Tokenizer {

  public List<Token> tokenize(String css) throws ScriptException {
    ScriptEngineManager factory = new ScriptEngineManager();
    ScriptEngine engine = factory.getEngineByName("JavaScript");
    InputStream tokenizeScript = Tokenizer.class.getClassLoader().getResourceAsStream("tokenize.js");
    engine.eval(new InputStreamReader(tokenizeScript));
    String cssInput = "tokenize('" + css.replace("'", "\\'") + "')";
    Object tokens = engine.eval(cssInput);
    return extractTokens(tokens);
  }

  private static List<Token> extractTokens(Object tokens) {
    Map<String, Object> tokensArray = (Map<String, Object>) tokens;
    List<Token> resultList = new ArrayList<>();
    for (Object tokenObject : tokensArray.values()) {
      Map<String, Object> tokenProperties = (Map<String, Object>) tokenObject;
      Token cssToken;
      String text = tokenProperties.get("1").toString();
      Type type = computeType(tokenProperties.get("0").toString(), text);
      Integer startLine = (Integer) tokenProperties.get("2");
      if (tokenProperties.size() < 4) {
        // skip whitespace
        continue;
      } else {
        Integer startColumn = ((Double) tokenProperties.get("3")).intValue();
        // all cases except for punctuator type
        if (tokenProperties.size() == 6) {
          Integer endLine = (Integer) tokenProperties.get("4");
          Integer endColumn = ((Double) tokenProperties.get("5")).intValue();
          cssToken = new Token(type, text, startLine, startColumn, endLine, endColumn);
        } else {
          // is punctuator
          cssToken = new Token(type, text, startLine, startColumn, startLine, startColumn);
        }
      }

      resultList.add(cssToken);
    }
    return resultList;
  }

  private static Type computeType(String type, String text) {
    switch (type) {
      case "at-word":
        return Type.AT_WORD;
      case "word":
        return Type.WORD;
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
