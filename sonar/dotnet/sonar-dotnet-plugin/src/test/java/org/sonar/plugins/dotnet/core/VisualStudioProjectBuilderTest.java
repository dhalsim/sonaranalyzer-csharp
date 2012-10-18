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
package org.sonar.plugins.dotnet.core;

import org.sonar.plugins.dotnet.api.visualstudio.VisualStudioProject;
import org.sonar.plugins.dotnet.api.visualstudio.VisualStudioSolution;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.bootstrap.ProjectReactor;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.SonarException;
import org.sonar.plugins.dotnet.api.DotNetConfiguration;
import org.sonar.plugins.dotnet.api.DotNetConstants;
import org.sonar.plugins.dotnet.api.MicrosoftWindowsEnvironment;
import org.sonar.test.TestUtils;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class VisualStudioProjectBuilderTest {

  private static File fakeSdkDir;
  private static File fakeSilverlightDir;
  private MicrosoftWindowsEnvironment microsoftWindowsEnvironment;
  private ProjectReactor reactor;
  private ProjectDefinition root;
  private File solutionBaseDir;
  private VisualStudioProjectBuilder projectBuilder;
  private Settings settings;

  @BeforeClass
  public static void initResources() {
    fakeSdkDir = new File("target/sonar/SDK");
    fakeSdkDir.mkdirs();
    fakeSilverlightDir = new File("target/sonar/Silverlight");
    fakeSilverlightDir.mkdirs();
  }

  @AfterClass
  public static void removeResources() {
    fakeSdkDir.delete();
    fakeSilverlightDir.delete();
  }

  @Before
  public void initBuilder() {
    microsoftWindowsEnvironment = new MicrosoftWindowsEnvironment();
    settings = Settings.createForComponent(new DotNetCorePlugin());
    settings.setProperty("sonar.language", "cs");
    settings.setProperty(DotNetConstants.DOTNET_4_0_SDK_DIR_KEY, fakeSdkDir.getAbsolutePath());
    settings.setProperty(DotNetConstants.SILVERLIGHT_4_MSCORLIB_LOCATION_KEY, fakeSilverlightDir.getAbsolutePath());
    solutionBaseDir = TestUtils.getResource("/solution/Example");
    root = ProjectDefinition.create().setBaseDir(solutionBaseDir).setWorkDir(new File(solutionBaseDir, "WORK-DIR"));
    root.setVersion("1.0");
    root.setKey("groupId:artifactId");
    reactor = new ProjectReactor(root);
    projectBuilder = new VisualStudioProjectBuilder(reactor, new DotNetConfiguration(settings), microsoftWindowsEnvironment);
  }

  @Test
  public void testNotValidSdkDir() throws Exception {
    settings.setProperty(DotNetConstants.DOTNET_4_0_SDK_DIR_KEY, "foo");
    projectBuilder = new VisualStudioProjectBuilder(reactor, new DotNetConfiguration(settings), microsoftWindowsEnvironment);
    projectBuilder.build(reactor);
    // a warning must have been logged, but the value is ketp as is
    assertThat(microsoftWindowsEnvironment.getDotnetSdkDirectory()).isEqualTo(new File("foo"));
  }

  @Test
  public void testNotValidSilverlightDir() throws Exception {
    settings.setProperty(DotNetConstants.SILVERLIGHT_4_MSCORLIB_LOCATION_KEY, "foo");
    projectBuilder = new VisualStudioProjectBuilder(reactor, new DotNetConfiguration(settings), microsoftWindowsEnvironment);
    projectBuilder.build(reactor);
    // a warning must have been logged, but the value is ketp as is
    assertThat(microsoftWindowsEnvironment.getSilverlightDirectory()).isEqualTo(new File("foo"));
  }

  @Test(expected = SonarException.class)
  public void testNonExistingSlnFile() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "NonExistingFile.sln");
    projectBuilder.build(reactor);
  }

  @Test
  public void testCorrectlyConfiguredProject() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "Example.sln");
    projectBuilder.build(reactor);
    // check that the configuration is OK
    assertThat(microsoftWindowsEnvironment.getDotnetVersion(), is("4.0"));
    assertThat(microsoftWindowsEnvironment.getDotnetSdkDirectory().getAbsolutePath(), is(fakeSdkDir.getAbsolutePath()));
    assertThat(microsoftWindowsEnvironment.getSilverlightVersion(), is("4"));
    assertThat(microsoftWindowsEnvironment.getSilverlightDirectory().getAbsolutePath(), is(fakeSilverlightDir.getAbsolutePath()));
    assertThat(microsoftWindowsEnvironment.getWorkingDirectory(), is("WORK-DIR"));
    // check that the solution is built
    VisualStudioSolution solution = microsoftWindowsEnvironment.getCurrentSolution();
    assertNotNull(solution);
    assertThat(solution.getProjects().size(), is(3));
    assertThat(microsoftWindowsEnvironment.getCurrentProject("Example.Application").getSourceFiles().size(), is(2));
    assertThat(microsoftWindowsEnvironment.getCurrentProject("Example.Core").getSourceFiles().size(), is(6));
    // check the multi-module definition is correct
    assertThat(reactor.getRoot().getSubProjects().size(), is(3));
    assertThat(reactor.getRoot().getSourceFiles().size(), is(0));
    ProjectDefinition subProject = reactor.getRoot().getSubProjects().get(0);
    VisualStudioProject vsProject = microsoftWindowsEnvironment.getCurrentProject("Example.Application");
    assertThat(subProject.getName(), is("Example.Application"));
    assertThat(subProject.getKey(), is("groupId:Example.Application"));
    assertThat(subProject.getVersion(), is("1.0"));
    assertThat(subProject.getBaseDir(), is(vsProject.getDirectory()));
    assertThat(subProject.getWorkDir(), is(new File(vsProject.getDirectory(), "WORK-DIR")));
    assertThat(subProject.getSourceDirs().iterator().next(), notNullValue());
    assertTrue(subProject.getTestDirs().isEmpty());
    ProjectDefinition testSubProject = reactor.getRoot().getSubProjects().get(2);
    assertThat(testSubProject.getName(), is("Example.Core.Tests"));
    assertThat(testSubProject.getTestDirs().iterator().next(), notNullValue());
    assertTrue(testSubProject.getSourceDirs().isEmpty());
  }

  @Test
  public void testCorrectlyConfiguredProjectInSafeMode() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "Example.sln");
    settings.setProperty(DotNetConstants.KEY_GENERATION_STRATEGY_KEY, "safe");
    projectBuilder.build(reactor);
    // check that the configuration is OK
    assertThat(microsoftWindowsEnvironment.getDotnetVersion(), is("4.0"));
    assertThat(microsoftWindowsEnvironment.getDotnetSdkDirectory().getAbsolutePath(), is(fakeSdkDir.getAbsolutePath()));
    assertThat(microsoftWindowsEnvironment.getSilverlightVersion(), is("4"));
    assertThat(microsoftWindowsEnvironment.getSilverlightDirectory().getAbsolutePath(), is(fakeSilverlightDir.getAbsolutePath()));
    assertThat(microsoftWindowsEnvironment.getWorkingDirectory(), is("WORK-DIR"));
    // check that the solution is built
    VisualStudioSolution solution = microsoftWindowsEnvironment.getCurrentSolution();
    assertNotNull(solution);
    assertThat(solution.getProjects().size(), is(3));
    assertThat(microsoftWindowsEnvironment.getCurrentProject("Example.Application").getSourceFiles().size(), is(2));
    assertThat(microsoftWindowsEnvironment.getCurrentProject("Example.Core").getSourceFiles().size(), is(6));
    // check the multi-module definition is correct
    assertThat(reactor.getRoot().getSubProjects().size(), is(3));
    assertThat(reactor.getRoot().getSourceFiles().size(), is(0));
    ProjectDefinition subProject = reactor.getRoot().getSubProjects().get(0);
    VisualStudioProject vsProject = microsoftWindowsEnvironment.getCurrentProject("Example.Application");
    assertThat(subProject.getName(), is("Example.Application"));
    assertThat(subProject.getKey(), is("groupId:artifactId:Example.Application"));
    assertThat(subProject.getVersion(), is("1.0"));
    assertThat(subProject.getBaseDir(), is(vsProject.getDirectory()));
    assertThat(subProject.getWorkDir(), is(new File(vsProject.getDirectory(), "WORK-DIR")));
    assertThat(subProject.getSourceDirs().iterator().next(), notNullValue());
    assertTrue(subProject.getTestDirs().isEmpty());
    ProjectDefinition testSubProject = reactor.getRoot().getSubProjects().get(2);
    assertThat(testSubProject.getName(), is("Example.Core.Tests"));
    assertThat(testSubProject.getTestDirs().iterator().next(), notNullValue());
    assertTrue(testSubProject.getSourceDirs().isEmpty());
  }

  @Test
  public void testNoSpecifiedSlnFileButOneFound() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "");
    projectBuilder = new VisualStudioProjectBuilder(reactor, new DotNetConfiguration(settings), microsoftWindowsEnvironment);
    projectBuilder.build(reactor);
    assertThat(microsoftWindowsEnvironment.getDotnetSdkDirectory().getAbsolutePath(), is(fakeSdkDir.getAbsolutePath()));
    VisualStudioSolution solution = microsoftWindowsEnvironment.getCurrentSolution();
    assertNotNull(solution);
    assertThat(solution.getProjects().size(), is(3));
  }

  @Test(expected = SonarException.class)
  public void testNoSpecifiedSlnFileButNoneFound() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "");
    root.setBaseDir(TestUtils.getResource("/solution"));
    projectBuilder.build(reactor);
  }

  @Test(expected = SonarException.class)
  public void testNoSpecifiedSlnFileButTooManyFound() throws Exception {
    settings.setProperty(DotNetConstants.SOLUTION_FILE_KEY, "");
    root.setBaseDir(TestUtils.getResource("/solution/FakeSolutionWithTwoSlnFiles"));
    projectBuilder.build(reactor);
  }

}
