/*
 * Sonar JavaScript Plugin
 * Copyright (C) 2011 Eriks Nukis and SonarSource
 * dev@sonar.codehaus.org
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.javascript.complexity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.PersistenceMode;
import org.sonar.api.measures.RangeDistributionBuilder;
import org.sonar.api.profiles.RulesProfile;
import org.sonar.api.resources.InputFile;
import org.sonar.api.resources.Project;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.Violation;
import org.sonar.plugins.javascript.core.JavaScript;
import org.sonar.plugins.javascript.jslint.JavaScriptRuleRepository;
import org.sonar.plugins.javascript.jslint.JsLintRuleManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public final class JavaScriptComplexitySensor implements Sensor {

  private final static Logger LOG = LoggerFactory.getLogger(JavaScriptComplexitySensor.class);
  private JavaScript javascript;
  private RulesProfile rulesProfile;

  private final Number[] FUNCTIONS_DISTRIB_BOTTOM_LIMITS = { 1, 2, 4, 6, 8, 10, 12, 20, 30 };
  private final Number[] FILES_DISTRIB_BOTTOM_LIMITS = { 0, 5, 10, 20, 30, 60, 90 };

  public JavaScriptComplexitySensor(JavaScript javascript, RulesProfile rulesProfile) {
    this.javascript = javascript;
    this.rulesProfile = rulesProfile;
  }

  public boolean shouldExecuteOnProject(Project project) {
    return javascript.equals(project.getLanguage());
  }

  public void analyse(Project project, SensorContext sensorContext) {
    for (InputFile inputFile : project.getFileSystem().mainFiles(JavaScript.KEY)) {
      try {
        analyzeFile(inputFile, project, sensorContext);
      } catch (Exception e) {
        LOG.error("Can not analyze the file " + inputFile.getFileBaseDir() + inputFile.getRelativePath(), e);
      }
    }
  }

  protected void analyzeFile(InputFile inputFile, Project project, SensorContext sensorContext) throws IOException {

    try {
      org.sonar.api.resources.File resource = org.sonar.api.resources.File.fromIOFile(inputFile.getFile(), project);

      JavaScriptComplexityAnalyzer analyzer = new JavaScriptComplexityAnalyzer();
      List<JavaScriptFunction> functions = analyzer.analyzeComplexity(new FileInputStream(inputFile.getFile()));
      
      // FUNCTIONS
      sensorContext.saveMeasure(resource, CoreMetrics.FUNCTIONS, (double) functions.size());

      // COMPLEXITY
      int fileComplexity = 0;
      for (JavaScriptFunction function : functions) {
        fileComplexity += function.getComplexity();
      }
      sensorContext.saveMeasure(resource, CoreMetrics.COMPLEXITY, Double.valueOf(fileComplexity));

      // FILE_COMPLEXITY_DISTRIBUTION
      RangeDistributionBuilder fileDistribution = new RangeDistributionBuilder(CoreMetrics.FILE_COMPLEXITY_DISTRIBUTION,
          FILES_DISTRIB_BOTTOM_LIMITS);
      fileDistribution.add(Double.valueOf(fileComplexity));
      sensorContext.saveMeasure(resource, fileDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));

      // FUNCTION_COMPLEXITY
      if ( !functions.isEmpty()) {
        sensorContext.saveMeasure(resource, CoreMetrics.FUNCTION_COMPLEXITY, Double.valueOf(fileComplexity) / functions.size());
      }

      // FUNCTION_COMPLEXITY_DISTRIBUTION
      RangeDistributionBuilder functionDistribution = new RangeDistributionBuilder(CoreMetrics.FUNCTION_COMPLEXITY_DISTRIBUTION,
          FUNCTIONS_DISTRIB_BOTTOM_LIMITS);
      for (JavaScriptFunction function : functions) {
        functionDistribution.add(Double.valueOf(function.getComplexity()));
      }
      sensorContext.saveMeasure(resource, functionDistribution.build().setPersistenceMode(PersistenceMode.MEMORY));

      // Maximum complexity exceed rule (CYCLOMATIC_COMPLEXITY)
      ActiveRule rule = rulesProfile.getActiveRule(JavaScriptRuleRepository.REPOSITORY_KEY, JsLintRuleManager.CYCLOMATIC_COMPLEXITY_KEY);
      if (rule != null) {
        int maxAllowedComplexity = Integer.parseInt(rule.getActiveRuleParams().get(0).getValue());
        for (JavaScriptFunction function : functions) {
          if (function.getComplexity() > maxAllowedComplexity) {
            Violation violation = Violation.create(rule, resource);

            violation.setLineId(function.getLine());
            violation
                .setMessage("Cyclomatic Complexity is " + function.getComplexity() + " (max allowed is " + maxAllowedComplexity + ").");

            sensorContext.saveViolation(violation);
          }
        }
      }

    } catch (JavaScriptPluginException e) {
      LOG.error("Could not analyze file: " + inputFile.getRelativePath(), e);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}