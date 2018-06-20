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

import com.sonar.sslr.impl.Lexer;

import static com.sonar.sslr.impl.channel.RegexpChannelBuilder.regexp;

public final class CssLexer {

  private static final String NEW_LINE = "(\r\n|\r|\n|\f)";
  private static final String WHITESPACE = "[\t\n\f\r ]";
  private static final String NON_ASCII = "[^\\p{ASCII}]";
  private static final String HEX_DIGIT = "0-9a-fA-F";
  private static final String ESCAPE = "(\\\\[" + HEX_DIGIT + "]{1,6}" + WHITESPACE + "?)|\\[^\r\n\f" + HEX_DIGIT + "]";

  private static final String PUNCTUATOR = "[!:,;%&+\\*-/=>\\(\\)\\[\\]\\{\\}]";

  private static final String MULTI_LINE_COMMENT = "/\\*(.|" + NEW_LINE + ")*?\\*/";
  private static final String INLINE_COMMENT = "//.*";
  private static final String COMMENT  = "(" + INLINE_COMMENT + "|" + MULTI_LINE_COMMENT + ")";

  private static final String NUMBER = "[+|-]?\\d*\\.?\\d+([a-z]+|%)?";

  private static final String NAME_CHAR = "[a-zA-Z0-9_-]|" + NON_ASCII + "|" + ESCAPE;
  private static final String NAME_START = "[a-zA-Z_]|" + NON_ASCII + "|" + ESCAPE;

  private static final String IDENTIFIER = "-?(" + NAME_START + ")(" + NAME_CHAR + ")*";
  private static final String AT_IDENTIFIER = "@+" + IDENTIFIER;
  private static final String HASH_IDENTIFIER = "#(" + NAME_CHAR + ")+";
  private static final String DOLLAR_IDENTIFIER = "\\$(" + NAME_CHAR + ")+";

  private static final String DOUBLE_QUOTE_STRING = "~?\"([^\"\\\\\r\n\f]|" + ESCAPE + "|\\\\" + NEW_LINE + ")*\"";
  private static final String SINGLE_QUOTE_STRING = "~?'([^'\\\\\r\n\f]|" + ESCAPE + "|\\\\" + NEW_LINE + ")*'";
  private static final String STRING = "(" + SINGLE_QUOTE_STRING + "|" + DOUBLE_QUOTE_STRING + ")";

  private CssLexer() {
  }

  public static Lexer create() {
    return Lexer.builder()
      .withFailIfNoChannelToConsumeOneCharacter(false)

      .withChannel(regexp(CssTokenType.COMMENT, COMMENT))
      .withChannel(regexp(CssTokenType.STRING, STRING))
      .withChannel(regexp(CssTokenType.AT_IDENTIFIER, AT_IDENTIFIER))
      .withChannel(regexp(CssTokenType.HASH_IDENTIFIER, HASH_IDENTIFIER))
      .withChannel(regexp(CssTokenType.DOLLAR_IDENTIFIER, DOLLAR_IDENTIFIER))
      .withChannel(regexp(CssTokenType.IDENTIFIER, IDENTIFIER))
      .withChannel(regexp(CssTokenType.NUMBER, NUMBER))
      .withChannel(regexp(CssTokenType.PUNCTUATOR, PUNCTUATOR))

      .build();
  }
}
