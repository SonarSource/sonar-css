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

public class Token {

  public enum Type {
    COMMENT,
    STRING,
    WORD,
    AT_WORD,
    BRACKETS,
    PUNCTUATOR
  }

  Type type;
  String text;
  Integer startLine;
  Integer startColumn;
  Integer endLine;
  Integer endColumn;

  public Token(Type type, String text, Integer startLine, Integer startColumn, Integer endLine, Integer endColumn) {
    this.text = text;
    this.type = type;
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  @Override
  public String toString() {
    return "Token [type=" + type + ", text=" + text + ", startLine=" + startLine + ", startColumn=" + startColumn + ", endLine=" + endLine + ", endColumn=" + endColumn + "]";
  }

}
