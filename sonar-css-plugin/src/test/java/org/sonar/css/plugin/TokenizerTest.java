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
  public void should_parse_less_variables_variable() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@primary:  green;" +
      "@secondary: blue;" +
      "" +
      ".section {" +
      "  @color: primary;" +
      "" +
      "  .element {" +
      "    color: @@color;" +
      "  }" +
      "}");
    assertThat(tokenList.get(15).type).isEqualTo(Token.Type.AT_WORD);
    assertThat(tokenList.get(15).text).isEqualTo("@@color");
    assertThat(tokenList.get(15).startLine).isEqualTo(1);
    assertThat(tokenList.get(15).startColumn).isEqualTo(86);
    assertThat(tokenList.get(15).endLine).isEqualTo(1);
    assertThat(tokenList.get(15).endColumn).isEqualTo(92);
  }

  @Test
  public void should_parse_less_maths() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".test {" +
      "  grid-column: 3 / 6;" +
      "  width: 40px + 2px;" +
      "  height: 42px;" +
      "}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_variables() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@link-color: #428bca;"
      + "@link-color-hover: darken(@link-color, 10%);"
      + "a," +
      ".link {" +
      "  color: @link-color;"
      + "}"
      + "a:hover {"
      + "  color: @link-color-hover;"
      + "}"
      + ".widget {"
      + " color: #fff;" +
      "  background: @link-color;"
      + "}");
    assertThat(tokenList.get(1).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_lazy_eval() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@var: 0;"
      + ".class {"
      + "  @var: 1;"
      + "  .brass {"
      + "    @var: 2;"
      + "  }"
      + "}");
    assertThat(tokenList.get(5).type).isEqualTo(Token.Type.AT_WORD);
  }

  @Test
  public void should_parse_less_properties_as_variables() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".block { "
      + "  color: red;"
      + "  .inner {"
      + "    background-color: $color;"
      + "  } "
      + "} ");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_parent_selectors() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".button {"
      + "  &-class1 {"
      + "    background-image: url(\"test.png\");"
      + "  }"
      + "  &-class2 {"
      + "    background-image: url(\"test.png\");"
      + "  }"
      + "}");
    assertThat(tokenList.get(2).type).isEqualTo(Token.Type.WORD);
    assertThat(tokenList.get(2).text).isEqualTo("&-class1");
  }

  @Test
  public void should_parse_less_multiple_imports() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@import \"t.css\";"
      + "@import \"test.css\";"
      + "@import url(\"path:t.css\");"
      + "#header {"
      + "  .rounded-corners;"
      + "}"
      + "#footer {"
      + "  .rounded-corners(10px);"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.AT_WORD);
    assertThat(tokenList.get(0).text).isEqualTo("@import");
  }

  @Test
  public void should_parse_less_extend_base_class() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".test {"
      + "  background-color: red;"
      + "}"
      + ".test2 {"
      + "  &:extend(.test);"
      + "  color: brown;"
      + "}");
    assertThat(tokenList.get(9).type).isEqualTo(Token.Type.WORD);
    assertThat(tokenList.get(9).text).isEqualTo("&");
  }

  @Test
  public void should_parse_less_mixins() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".mixin(@color: red;) {"
      + "box-shadow+: inset 0 0 10px #555;"
      + "color: @color"
      + "}.myclass"
      + "{"
      + " .mixin();"
      + "box-shadow+: 0 0 20px red;"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
    assertThat(tokenList.get(0).text).isEqualTo(".mixin");
  }

  @Test
  public void should_parse_less_namespaces() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("#outer {"
      + "  .inner {"
      + "    color: red;"
      + "  }"
      + "}"
      + ".c {"
      + "  #outer > .inner;"
      + "}"
      + "#namespace {"
      + "  .mixin() when (@mode=huge) {}"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_logical_operators() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".mixin (@a) when (isstring(@a)) and (@a < 0) {}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_detached_ruleset() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@my-rules: {"
      + "    .my-selector {"
      + "      @media t {"
      + "        color: red;"
      + "      }"
      + "    }"
      + "  };"
      + "@media (orientation:portrait) {"
      + "    @my-rules();"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.AT_WORD);
  }

  @Test
  public void should_parse_less_new_scope_rules() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@variable: global;"
      + "@detached-ruleset: {"
      + "  variable: @variable; "
      + "};"
      + "selector {"
      + "  @detached-ruleset();"
      + "  @variable: value;"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.AT_WORD);
  }

  @Test
  public void should_parse_sass_font() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("$font-stack: Helvetica, sans-serif;"
      + "body {"
      + " font: 10% $font-stack;"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_sass_extend() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".test{" +
      "    color: #333;" +
      "   }" +
      "   .test2 {" +
      "    @extend .test;" +
      "    border-color: green;" +
      "   }");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_scss_default_variable() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("$default-color: red !default;" +
      "p.message {" +
      "    color: $default-color;" +
      "}");
    assertThat(tokenList.get(3).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_scss_control_directives() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@each $temp in 't1' 't2' {" +
      "    .icon-#{$temp} {" +
      "        background-image: url('/imgs/#{$temp}.png');" +
      "    }" +
      "}");
    assertThat(tokenList.get(0).type).isEqualTo(Type.AT_WORD);
  }

  @Test
  public void should_parse_scss_mixin_arguments() throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@mixin foo($topPadding: 10px, $bottomPadding: 20px) {" +
      "}" +
      "p {" +
      "    @include foo($bottomPadding: 50px);" +
      "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.AT_WORD);
  }

  private static List<Token> assertToken(String input, int index, String value, Token.Type type) throws ScriptException {
    List<Token> tokenList = tokenizer.tokenize(input);
    assertThat(tokenList.get(index).type).isEqualTo(type);
    assertThat(tokenList.get(index).text).isEqualTo(value);

    return tokenList;
  }

  private static List<Token> assertToken(String input, int index, String value, Token.Type type, int line, int column, int endLine, int endColumn) throws ScriptException {
    List<Token> tokenList = assertToken(input, index, value, type);
    assertThat(tokenList.get(index).startLine).isEqualTo(line);
    assertThat(tokenList.get(index).startColumn).isEqualTo(column);
    assertThat(tokenList.get(index).endLine).isEqualTo(endLine);
    assertThat(tokenList.get(index).endColumn).isEqualTo(endColumn);

    return tokenList;
  }
}
