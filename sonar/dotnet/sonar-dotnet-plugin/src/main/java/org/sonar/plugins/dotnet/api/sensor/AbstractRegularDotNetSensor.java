/*
 * Sonar .NET Plugin :: Core
 * Copyright (C) 2010 Jose Chillan, Alexandre Victoor and SonarSource
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
package org.sonar.plugins.dotnet.api.sensor;

import org.sonar.plugins.dotnet.api.visualstudio.VisualStudioProject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Project;
import org.sonar.plugins.dotnet.api.DotNetConfiguration;
import org.sonar.plugins.dotnet.api.DotNetConstants;
import org.sonar.plugins.dotnet.api.MicrosoftWindowsEnvironment;

import java.io.File;
import java.util.Collection;

/**
 * Abstract Sensor for C# plugins that will be executed on every sub-project.
 * Should be renamed... (not so easy to do so since squid is closed source)
 */
public abstract class AbstractRegularDotNetSensor extends AbstractDotNetSensor {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractRegularDotNetSensor.class);

  protected final DotNetConfiguration configuration;

  /**
   * Creates an {@link AbstractRegularDotNetSensor} that has a {@link MicrosoftWindowsEnvironment} reference.
   * 
   * @param microsoftWindowsEnvironment
   *          the {@link MicrosoftWindowsEnvironment}
   */
  protected AbstractRegularDotNetSensor(Language language, DotNetConfiguration configuration, MicrosoftWindowsEnvironment microsoftWindowsEnvironment, String toolName,
      String executionMode) {
    super(language, microsoftWindowsEnvironment, toolName, executionMode);
    this.configuration = configuration;
  }

  protected boolean isTestSensor() {
    return false;
  }

  protected boolean isCilSensor() {
    return false;
  }

  protected final String[] getAssemblyPatterns() {
    String key = isTestSensor() ? DotNetConstants.TEST_ASSEMBLIES_KEY : DotNetConstants.ASSEMBLIES_TO_SCAN_KEY;
    return configuration.getStringArray(key);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return super.shouldExecuteOnProject(project) && (isTestProject(project) == isTestSensor()) && (!isCilSensor() || assembliesFound(project));
  }

  private boolean assembliesFound(Project project) {
    final boolean result;

    boolean reuseMode = MODE_REUSE_REPORT.equalsIgnoreCase(executionMode);
    if (reuseMode) {
      result = true;
    } else {

      final VisualStudioProject visualProject = getVSProject(project);
      Collection<File> assemblies;

      final String[] assemblyPatterns = getAssemblyPatterns();
      final String buildConfigurations = configuration.getString(DotNetConstants.BUILD_CONFIGURATION_KEY);
      final String buildPlatform = configuration.getString(DotNetConstants.BUILD_PLATFORM_KEY);
      if (assemblyPatterns == null || assemblyPatterns.length == 0) {
        assemblies = visualProject.getGeneratedAssemblies(buildConfigurations, buildPlatform);
      } else {
        assemblies = findFiles(project, assemblyPatterns);
        if (assemblies.isEmpty()) {
          // fall back to the default VS output folder
          assemblies = visualProject.getGeneratedAssemblies(buildConfigurations, buildPlatform);
        }
      }

      if (assemblies.isEmpty()) {
        LOG.warn("No assembly to check with " + toolName);
        result = false;
      } else {
        result = true;
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public org.sonar.api.resources.File fromIOFile(File file, Project project) {
    if (isTestSensor()) {
      return org.sonar.api.resources.File.fromIOFile(file, project.getFileSystem().getTestDirs());
    }
    return super.fromIOFile(file, project);
  }

}
