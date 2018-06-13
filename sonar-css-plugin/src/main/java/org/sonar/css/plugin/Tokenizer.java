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

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.css.plugin.Token.Type;

public class Tokenizer {

  private static final Logger LOG = Loggers.get(Tokenizer.class);

  public List<Token> tokenize(String css) throws ScriptException {
    ScriptEngineManager factory = new ScriptEngineManager();
    //ScriptEngine engine = factory.getEngineByName("nashorn");
    ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
    InputStream tokenizeScript = Tokenizer.class.getClassLoader().getResourceAsStream("tokenize.js");
    if (tokenizeScript == null) {
      LOG.info("tokenizeScript null");
    } else {
      LOG.info("tokenizeScript not null");
    }
    if (engine == null) {
      LOG.info("engine null");
    } else {
      LOG.info("engine not null");
    }
    engine.eval(new InputStreamReader(tokenizeScript, StandardCharsets.UTF_8));
    String cssInput = "tokenize('" + StringEscapeUtils.escapeJavaScript(css) + "')";
    Object tokens = engine.eval(cssInput);
    return extractTokens(tokens);
  }

  private static List<Token> extractTokens(Object tokens) {
    // tokens is result of call to javascript function tokenize(). It returns an array of arrays, where nested arrays
    // correspond to tokens. These array javascript objects mapped in Java to Map objects where array index is key.

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


          if (isTokenWithPunctuator(text, ",", startLine, endLine)) {
            resultList.addAll(splitTokenWithPunctuator(text, type, startLine, startColumn, endLine, endColumn));
          } else if (isTokenWithPunctuator(text, ":", startLine, endLine)) {
            resultList.addAll(splitTokenWithPunctuator(text, type, startLine, startColumn, endLine, endColumn));
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

  // Javascript tokenizer is not returning 2 tokens for words ending with a comma (e.g. foo,) and for words starting
  // with at symbol and endings with colon (e.g. @base:) so we need to split the word into 2 tokens (1 word without
  // the punctuator and 1 punctuator).
  // For the sake of simplicity we don't handle words ending with the punctuator on a new line.
  private static Boolean isTokenWithPunctuator(String text, String punctuator, Integer startLine, Integer endLine) {
    return text.length() > 1 && text.endsWith(punctuator) && startLine.equals(endLine);
  }

  private static List<Token> splitTokenWithPunctuator(String text, Type type, Integer startLine, Integer startColumn, Integer endLine, Integer endColumn) {
    List<Token> tokenList = new ArrayList<>();

    tokenList.add(new Token(type, text.substring(0, text.length() - 1), startLine, startColumn, endLine, endColumn - 1));
    tokenList.add(new Token(Type.PUNCTUATOR, text.substring(text.length() - 1), startLine, endColumn, endLine, endColumn));

    return tokenList;
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
