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

import java.io.FileNotFoundException;
import java.util.List;
import javax.script.ScriptException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TokenizerTest {

  private final static Tokenizer tokenizer = new Tokenizer();

  @Test
  public void should_parse_word() throws ScriptException, FileNotFoundException {
    List<Token> tokenList = tokenizer.tokenize("body {margin: 0;}");
    assertThat(tokenList).hasSize(7);
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
    assertThat(tokenList.get(0).text).isEqualTo("body");
    assertThat(tokenList.get(0).startLine).isEqualTo(1);
    assertThat(tokenList.get(0).startColumn).isEqualTo(1);
    assertThat(tokenList.get(0).endLine).isEqualTo(1);
    assertThat(tokenList.get(0).endColumn).isEqualTo(4);
  }

  @Test
  public void should_parse_number_to_word() throws ScriptException, FileNotFoundException {
    List<Token> tokenList = tokenizer.tokenize("input { "
      + "line-height: 1.15;"
      + "}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.WORD);
    assertThat(tokenList.get(4).text).isEqualTo("1.15");
    assertThat(tokenList.get(4).startLine).isEqualTo(1);
    assertThat(tokenList.get(4).startColumn).isEqualTo(22);
    assertThat(tokenList.get(4).endLine).isEqualTo(1);
    assertThat(tokenList.get(4).endColumn).isEqualTo(25);
  }

  @Test
  public void should_parse_brackets_to_brackets() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("input.grow { "
      + "-webkit-animation: grow 0.8s cubic-bezier(0.175, 0.885, 0.32, 1.275);"
      + "-moz-animation: grow 0.8s cubic-bezier(0.175, 0.885, 0.32, 1.275);"
      + "animation: grow 0.8s cubic-bezier(0.175, 0.885, 0.32, 1.275); "
      + "}");
    assertThat(tokenList.get(7).type).isEqualTo(Token.Type.BRACKETS);
    assertThat(tokenList.get(7).text).isEqualTo("(0.175, 0.885, 0.32, 1.275)");
    assertThat(tokenList.get(7).startLine).isEqualTo(1);
    assertThat(tokenList.get(7).startColumn).isEqualTo(55);
    assertThat(tokenList.get(7).endLine).isEqualTo(1);
    assertThat(tokenList.get(7).endColumn).isEqualTo(81);
  }

  @Test
  public void should_parse_string_to_string() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("input {"
      + "code: \"\";"
      + "description: \"empty stylesheet\";"
      + "}");
    assertThat(tokenList.get(8).type).isEqualTo(Token.Type.STRING);
    assertThat(tokenList.get(8).text).isEqualTo("\"empty stylesheet\"");
    assertThat(tokenList.get(8).startLine).isEqualTo(1);
    assertThat(tokenList.get(8).startColumn).isEqualTo(30);
    assertThat(tokenList.get(8).endLine).isEqualTo(1);
    assertThat(tokenList.get(8).endColumn).isEqualTo(47);
  }

  @Test
  public void should_parse_at_word_to_at_word() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("test {" +
      "  code: @import \"foo.css\";" +
      "  description: \"blockless statement\"" +
      "}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.AT_WORD);
    assertThat(tokenList.get(4).text).isEqualTo("@import");
    assertThat(tokenList.get(4).startLine).isEqualTo(1);
    assertThat(tokenList.get(4).startColumn).isEqualTo(15);
    assertThat(tokenList.get(4).endLine).isEqualTo(1);
    assertThat(tokenList.get(4).endColumn).isEqualTo(21);
  }

  @Test
  public void should_parse_comment_to_comment() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("test {"
      + "{code: \"a {color: red;}\""
      + ",description: \".\""
      + "},"
      + "{code: \"@mixin name ($p) {}\"}"
      + "/* comment */"
      + "}");
    assertThat(tokenList.get(16).type).isEqualTo(Token.Type.COMMENT);
    assertThat(tokenList.get(16).text).isEqualTo("/* comment */");
    assertThat(tokenList.get(16).startLine).isEqualTo(1);
    assertThat(tokenList.get(16).startColumn).isEqualTo(79);
    assertThat(tokenList.get(16).endLine).isEqualTo(1);
    assertThat(tokenList.get(16).endColumn).isEqualTo(91);
  }

  @Test
  public void should_parse_less_variables_variable() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_maths() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".test {" +
      "  grid-column: 3 / 6;" +
      "  width: 40px + 2px;" +
      "  height: 42px;" +
      "}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_variables() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_lazy_eval() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_properties_as_variables() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".block { "
      + "  color: red;"
      + "  .inner {"
      + "    background-color: $color;"
      + "  } "
      + "} ");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_parent_selectors() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_multiple_imports() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_extend_base_class() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_mixins() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_namespaces() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_logical_operators() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize(".mixin (@a) when (isstring(@a)) and (@a < 0) {}");
    assertThat(tokenList.get(4).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_less_detached_ruleset() throws FileNotFoundException, ScriptException {
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
  public void should_parse_less_new_scope_rules() throws FileNotFoundException, ScriptException {
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
  public void should_parse_sass_font() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("$font-stack: Helvetica, sans-serif;"
      + "body {"
      + " font: 10% $font-stack;"
      + "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_sass_extend() throws FileNotFoundException, ScriptException {
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
  public void should_parse_scss_default_variable() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("$default-color: red !default;" +
      "p.message {" +
      "    color: $default-color;" +
      "}");
    assertThat(tokenList.get(3).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_scss_control_directives() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@each $temp in 't1' 't2' {" +
      "    .icon-#{$temp} {" +
      "        background-image: url('/imgs/#{$temp}.png');" +
      "    }" +
      "}");
    // assertThat(tokenList.get(0).type).isEqualTo(Token.Type.WORD);
  }

  @Test
  public void should_parse_scss_mixin_arguments() throws FileNotFoundException, ScriptException {
    List<Token> tokenList = tokenizer.tokenize("@mixin foo($topPadding: 10px, $bottomPadding: 20px) {" +
      "}" +
      "p {" +
      "    @include foo($bottomPadding: 50px);" +
      "}");
    assertThat(tokenList.get(0).type).isEqualTo(Token.Type.AT_WORD);
  }
}
