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

import java.util.List;
import java.util.Optional;

import javax.script.ScriptException;
import org.junit.Test;
import org.sonar.css.plugin.Token.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenizerTest {

  private final static Tokenizer tokenizer = new Tokenizer();
  
  @Test
  public void word() throws ScriptException {
    assertToken("bar { }", 0, "bar", Type.WORD);
    assertToken("bar: foo { }", 0, "bar", Type.WORD);
    assertToken("bar: foo-baz { }", 2, "foo-baz", Type.WORD);
    assertToken("foo bar { }", 1, "bar", Type.WORD);
    assertToken("#bar { }", 0, "#bar", Type.WORD);
    assertToken("foo.bar { }", 0, "foo.bar", Type.WORD);
    assertToken(".bar { }", 0, ".bar", Type.WORD);
    assertToken("bar { foo: 42; }", 2, "foo", Type.WORD);
    assertToken("bar { foo: baz; }", 4, "baz", Type.WORD);
    assertToken("foo , bar { }", 2, "bar", Type.WORD);
  }

  @Test
  public void semi_colon() throws ScriptException {
    assertToken("bar: foo { }", 1, ":", Type.PUNCTUATOR);
    assertToken("bar { foo; }", 3, ";", Type.PUNCTUATOR);
  }

  @Test
  public void comma() throws ScriptException {
    assertToken("foo , bar { }", 1, ",", Type.PUNCTUATOR);
    assertToken("foo, bar { }", 1, ",", Type.PUNCTUATOR);
  }

  @Test
  public void number_as_word() throws ScriptException {
    assertToken("bar { foo: 1.15; }", 4, "1.15", Type.WORD);
    assertToken("bar { foo: 1; }", 4, "1", Type.WORD);
    assertToken("bar { foo: 1.15px; }", 4, "1.15px", Type.WORD);
    assertToken("bar { foo: 1.15%; }", 4, "1.15%", Type.WORD);
    assertToken("bar { foo: 1px; }", 4, "1px", Type.WORD);
    assertToken("bar { foo: 1em/150%; }", 4, "1em/150%", Type.WORD);
  }

  @Test
  public void brackets() throws ScriptException {
    assertToken("bar { foo: (1.15); }", 4, "(1.15)", Type.BRACKETS);
    assertToken("bar { foo: ( 1.15 ); }", 4, "( 1.15 )", Type.BRACKETS);
    assertToken("bar { foo: (1.15 1 0px); }", 4, "(1.15 1 0px)", Type.BRACKETS);
    assertToken("bar { foo: (1.15, 1, 0px); }", 4, "(1.15, 1, 0px)", Type.BRACKETS);
    assertToken("bar { content: string(doctitle); }", 5, "(doctitle)", Type.BRACKETS);
    assertToken("bar { string-set: booktitle content(); }", 6, "()", Type.BRACKETS);
    assertToken("bar { a: b(attr(href, url), c) \")\"; }", 7, "(href, url)", Type.BRACKETS);
  }

  @Test
  public void strings() throws ScriptException {
    assertToken("bar { foo: \"\"; }", 4, "\"\"", Type.STRING);
    assertToken("bar { foo: \"hello, world\"; }", 4, "\"hello, world\"", Type.STRING);
  }

  @Test
  public void at_word() throws ScriptException {
    assertToken("@bar { }", 0, "@bar", Type.AT_WORD);
  }

  @Test
  public void comment() throws ScriptException {
    assertToken("/* foo */", 0, "/* foo */", Type.COMMENT);
    assertToken("foo { a: /* foo */ 42; }", 4, "/* foo */", Type.COMMENT);
    assertToken("/* \n"
      + "  this is a comment\n"
      + "  and it is awesome because\n"
      + "  it is multiline!\n"
      + "*/", 0, "/* \n"
      + "  this is a comment\n"
      + "  and it is awesome because\n"
      + "  it is multiline!\n"
      + "*/", Type.COMMENT, 1, 1, 5, 2);
    assertToken("foo { a: /* foo\nbar*/ 42; }", 4, "/* foo\nbar*/", Type.COMMENT, 1, 10, 2, 5);
  }

  @Test
  public void hashtag() throws ScriptException {
    assertToken("bar { color: #333; }", 4, "#333", Type.WORD);
    assertToken("bar { color: #e535ab; }", 4, "#e535ab", Type.WORD);
    assertToken("#table-of-contents + ul li { list-style: none; }", 0, "#table-of-contents", Type.WORD);
  }

  @Test
  public void scss_variable() throws ScriptException {
    assertToken("$font-stack: Helvetica;", 0, "$font-stack", Type.WORD);
    assertToken("p.message-#{$alertClass} { color: red; }", 3, "$alertClass", Type.WORD);
    assertToken("$message-color: blue !default;", 3, "!default", Type.WORD);
  }

  @Test
  public void scss_import() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@import 'base';");

    assertThat(tokenList.size()).isEqualTo(3);
    assertToken(tokenList, 0, "@import", Type.AT_WORD);
    assertToken(tokenList, 1, "'base'", Type.STRING);
    assertToken(tokenList, 2, ";", Type.PUNCTUATOR);
  }

  @Test
  public void scss_role() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("article[role=\"main\"] { width: 1px; }");

    assertThat(tokenList.size()).isEqualTo(11);
    assertToken(tokenList, 0, "article", Type.WORD);
    assertToken(tokenList, 1, "[", Type.PUNCTUATOR);
    assertToken(tokenList, 2, "role=", Type.WORD);
    assertToken(tokenList, 3, "\"main\"", Type.STRING);
    assertToken(tokenList, 4, "]", Type.PUNCTUATOR);
  }

  @Test
  public void scss_operators() throws ScriptException {
    assertToken("foo { width: 300px + 960px; }", 5, "+", Type.WORD);
    assertToken("foo { width: 300px - 960px; }", 5, "-", Type.WORD);
    assertToken("foo { width: 300px * 960px; }", 5, "*", Type.WORD);
    assertToken("foo { width: 300px / 960px; }", 5, "/", Type.WORD);
  }

  @Test
  public void scss_parent_selector() throws ScriptException {
    assertToken("a { &:hover { color: red; } }", 2, "&", Type.WORD);
    assertToken("p { body.no-touch & { display: none; } }", 3, "&", Type.WORD);
  }

  @Test
  public void scss_control_directives() throws ScriptException {
    assertToken("@if ($debug) { }", 0, "@if", Type.AT_WORD);
    assertToken("@each $name in 'save' 'cancel' { }", 0, "@each", Type.AT_WORD);
  }

  @Test
  public void less_variable() throws ScriptException {
    assertToken("@nice-blue: #5B83AD;", 0, "@nice-blue:", Type.AT_WORD);
    assertToken("foo { color: @@color; }", 4, "@@color", Type.AT_WORD);
  }

  @Test
  public void less_operators() throws ScriptException {
    assertToken("@base: 2cm * 3mm;", 2, "*", Type.WORD);
  }

  @Test
  public void less_escaping() throws ScriptException {
    assertToken("@min768: ~\"(min-width: 768px)\";", 1, "~", Type.WORD);
  }

  @Test
  public void less_comment() throws ScriptException {
    // FIXME: Less allows // comment which are not supported by our current tokenizer
    //assertToken("// Get in line!", 0, "Get in line!", Type.COMMENT);

    assertToken("/* One heck of a block\n * style comment! */", 0, "/* One heck of a block\n * style comment! */", Type.COMMENT);
  }

  private static void assertToken(String input, int index, String value, Token.Type type) throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(input);
    assertToken(tokenList, index, value, type);
  }

  private static void assertToken(String input, int index, String value, Token.Type type, int line, int column, int
    endLine, int endColumn) throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(input);
    assertToken(tokenList, index, value, type, line, column, endLine, endColumn);
  }

  private static void assertToken(List<Token> tokenList, int index, String value, Token.Type type) {
    assertThat(tokenList.get(index).type).isEqualTo(type);
    assertThat(tokenList.get(index).text).isEqualTo(value);
  }

  private static void assertToken(List<Token> tokenList, int index, String value, Token.Type type, int line, int column, int endLine, int endColumn) {
    assertToken(tokenList, index, value, type);
    assertThat(tokenList.get(index).startLine).isEqualTo(line);
    assertThat(tokenList.get(index).startColumn).isEqualTo(column);
    assertThat(tokenList.get(index).endLine).isEqualTo(endLine);
    assertThat(tokenList.get(index).endColumn).isEqualTo(endColumn);
  }
}
