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
/*
 * Created on Apr 16, 2009
 */
package org.sonar.plugins.dotnet.api.visualstudio;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.resources.Project;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A visual studio solution model.
 * 
 * @author Fabrice BELLINGARD
 * @author Jose CHILLAN Apr 16, 2009
 */
public class VisualStudioSolution {

  private static final Logger LOG = LoggerFactory.getLogger(VisualStudioSolution.class);

  private File solutionFile;
  private File solutionDir;
  private String name;
  private List<VisualStudioProject> projects;
  private List<BuildConfiguration> buildConfigurations;

  public VisualStudioSolution(File solutionFile, List<VisualStudioProject> projects) {
    this.solutionFile = solutionFile;
    this.solutionDir = solutionFile.getParentFile();
    this.projects = projects;
    initializeFileAssociations();

    removeAssemblyNameDuplicates();

  }

  /**
   * Remove some projects
   * 
   * @param skippedProjects
   *          List of the excluded projects, using ',' as delimiter
   */
  public void filterProjects(String skippedProjects) {
    if (StringUtils.isEmpty(skippedProjects)) {
      return;
    }

    Set<String> skippedProjectSet = new HashSet<String>();
    skippedProjectSet.addAll(Arrays.asList(StringUtils.split(skippedProjects, ',')));

    ListIterator<VisualStudioProject> projectIterator = projects.listIterator();
    while (projectIterator.hasNext()) {
      VisualStudioProject visualStudioProject = projectIterator.next();
      if (skippedProjectSet.contains(visualStudioProject.getName())) {
        projectIterator.remove();
      }
    }
  }

  /**
   * Override default locations for the assemblies generated by the solution
   * 
   * @param assemblyDirectories
   *          A map where keys are project names and values path to the generated assemblies?
   */
  public void overrideAssemblyDirectories(Map<String, String> assemblyDirectories) {
    if (assemblyDirectories == null) {
      return;
    }

    Map<String, VisualStudioProject> projectMap = new HashMap<String, VisualStudioProject>();
    for (VisualStudioProject project : projects) {
      projectMap.put(project.getName(), project);
    }
    for (Map.Entry<String, String> entry : assemblyDirectories.entrySet()) {
      VisualStudioProject project = projectMap.get(entry.getKey());
      if (project == null) {
        LOG.error("Unknown project name '" + entry.getKey() + "' used in assembly directories settings");
      } else {
        project.setForcedOutputDir(entry.getValue());
      }
    }
  }

  private void removeAssemblyNameDuplicates() {
    Map<String, VisualStudioProject> projectMap = new HashMap<String, VisualStudioProject>();
    for (VisualStudioProject project : projects) {
      String assemblyName = project.getAssemblyName();
      if (projectMap.containsKey(assemblyName)) {
        int i = 1;
        String newAssemblyName;
        do {
          i++;
          newAssemblyName = assemblyName + "_" + i;
        } while (projectMap.containsKey(newAssemblyName));
        project.setAssemblyName(newAssemblyName);
        projectMap.put(newAssemblyName, project);
      } else {
        projectMap.put(assemblyName, project);
      }
    }
  }

  /**
   * Clean-up file/project associations in order to avoid having the same file in several projects.
   */
  private void initializeFileAssociations() {
    Set<File> csFiles = new HashSet<File>();
    for (VisualStudioProject project : projects) {
      Set<File> projectFiles = project.getSourceFileMap().keySet();
      Set<File> projectFilesToRemove = new HashSet<File>();
      for (File file : projectFiles) {
        if (getProjectByLocation(file) == null) {
          projectFilesToRemove.add(file);
        }
      }
      // remove files not present in the project directory
      projectFiles.removeAll(projectFilesToRemove);

      // remove files present in other projects
      projectFiles.removeAll(csFiles);

      csFiles.addAll(projectFiles);
    }
  }

  /**
   * Gets the project a cs file belongs to.
   * 
   * @param file
   * @return the project contains the file, or <code>null</code> if none is matching
   */
  public VisualStudioProject getProject(File file) {
    for (VisualStudioProject project : projects) {
      if (project.contains(file)) {
        return project;
      }
    }
    return null;
  }

  public VisualStudioProject getProjectFromSonarProject(Project sonarProject) {
    String currentProjectName = sonarProject.getName();
    String branch = sonarProject.getBranch();
    for (VisualStudioProject project : projects) {
      final String vsProjectName;
      if (StringUtils.isEmpty(branch)) {
        vsProjectName = project.getName();
      } else {
        vsProjectName = project.getName() + " " + branch;
      }
      if (currentProjectName.equals(vsProjectName)) {
        return project;
      }
    }
    return null;
  }

  /**
   * Gets the project whose base directory contains the file/directory.
   * 
   * @param file
   *          the file to look for
   * @return the associated project, or <code>null</code> if none is matching
   */
  public final VisualStudioProject getProjectByLocation(File file) {
    String canonicalPath;
    try {
      canonicalPath = file.getCanonicalPath();
      for (VisualStudioProject project : projects) {
        File directory = project.getDirectory();
        String projectFolderPath = directory.getPath();
        if (canonicalPath.startsWith(projectFolderPath) && project.isParentDirectoryOf(file)) {
          return project;
        }
      }
    } catch (IOException e) {
      LOG.debug("getProjectByLocation i/o exception", e);
    }

    return null;
  }

  /**
   * Returns the solutionFile.
   * 
   * @return The solutionFile to return.
   */
  public File getSolutionFile() {
    return this.solutionFile;
  }

  /**
   * Returns the solutionDir.
   * 
   * @return The solutionDir to return.
   */
  public File getSolutionDir() {
    return this.solutionDir;
  }

  /**
   * Gets a project by its assembly name.
   * 
   * @param assemblyName
   *          the name of the assembly
   * @return the project, or <code>null</code> if not found
   */
  public VisualStudioProject getProject(String assemblyName) {
    VisualStudioProject result = null;
    for (VisualStudioProject project : projects) {
      if (assemblyName.equalsIgnoreCase(project.getAssemblyName())) {
        result = project;
        break;
      }
    }
    if (result == null) {
      // perhaps a web project
      for (VisualStudioProject project : projects) {
        if (project instanceof VisualStudioWebProject) {
          VisualStudioWebProject webProject = (VisualStudioWebProject) project;
          if (webProject.getWebAssemblyNames().contains(assemblyName)) {
            result = project;
            break;
          }
        }
      }
    }
    return result;
  }

  public VisualStudioProject getProject(UUID projectGuid) {
    for (VisualStudioProject p : projects) {
      if (p.getProjectGuid().equals(projectGuid)) {
        return p;
      }
    }

    return null;
  }

  /**
   * Returns the projects.
   * 
   * @return The projects to return.
   */
  public List<VisualStudioProject> getProjects() {
    return this.projects;
  }

  /**
   * Returns the unit test projects.
   * 
   * @return The projects to return.
   */
  public List<VisualStudioProject> getUnitTestProjects() {
    List<VisualStudioProject> result = new ArrayList<VisualStudioProject>();
    for (VisualStudioProject visualStudioProject : projects) {
      if (visualStudioProject.isUnitTest()) {
        result.add(visualStudioProject);
      }
    }
    return result;
  }

  /**
   * Returns the integ test projects.
   * 
   * @return The projects to return.
   */
  public List<VisualStudioProject> getIntegTestProjects() {
    List<VisualStudioProject> result = new ArrayList<VisualStudioProject>();
    for (VisualStudioProject visualStudioProject : projects) {
      if (visualStudioProject.isIntegTest()) {
        result.add(visualStudioProject);
      }
    }
    return result;
  }

  /**
   * Iterate through all the projects of the solution seeking for silverlight applications
   * 
   * @return true if a silverlight application is found
   */
  public boolean isSilverlightUsed() {
    final Iterator<VisualStudioProject> projectIterator = projects.iterator();
    boolean silverlightFound = false;
    while (projectIterator.hasNext() && !silverlightFound) {
      silverlightFound = projectIterator.next().isSilverlightProject();
    }
    return silverlightFound;
  }

  /**
   * Iterate through all the projects of the solution seeking for asp.net applications
   * 
   * @return true if an asp.net project is found
   */
  public boolean isAspUsed() {
    final Iterator<VisualStudioProject> projectIterator = projects.iterator();
    boolean aspFound = false;
    while (projectIterator.hasNext() && !aspFound) {
      aspFound = projectIterator.next().isWebProject();
    }
    return aspFound;
  }

  /**
   * Returns the name.
   * 
   * @return The name to return.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Sets the name.
   * 
   * @param name
   *          The name to set.
   */
  void setName(String name) {
    this.name = name;
  }

  /**
   * Returns the build configurations.
   * 
   * @return the list of build configurations.
   */
  public List<BuildConfiguration> getBuildConfigurations() {
    return buildConfigurations;
  }

  void setBuildConfigurations(List<BuildConfiguration> buildConfigurations) {
    this.buildConfigurations = buildConfigurations;
  }

  @Override
  public String toString() {
    return "Solution(path=" + solutionFile + ")";
  }

}
