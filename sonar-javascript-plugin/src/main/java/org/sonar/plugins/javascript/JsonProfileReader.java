/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2017 SonarSource SA
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
package org.sonar.plugins.javascript;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

public class JsonProfileReader {

  private JsonProfileReader() {
  }

  public static Set<String> ruleKeys(String pathToFile) {
    URL profileUrl = JsonProfileReader.class.getResource(pathToFile);
    try {
      Gson gson = new Gson();
      return gson.fromJson(Resources.toString(profileUrl, Charsets.UTF_8), Profile.class).ruleKeys;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read: " + profileUrl, e);
    }
  }

  private static class Profile {
    Set<String> ruleKeys;
  }


}
