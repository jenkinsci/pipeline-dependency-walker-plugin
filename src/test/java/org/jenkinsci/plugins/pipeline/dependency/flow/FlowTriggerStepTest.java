package org.jenkinsci.plugins.pipeline.dependency.flow;

import static junit.framework.TestCase.assertEquals;
import static org.jvnet.hudson.test.ToolInstallations.configureMaven3;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jenkinsci.plugins.pipeline.dependency.flow.helpers.TestRepositoryLocator;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import hudson.maven.MavenModuleSet;
import hudson.maven.local_repo.LocalRepositoryLocator;
import hudson.maven.local_repo.PerExecutorLocalRepositoryLocator;
import hudson.model.AbstractProject;
import hudson.tasks.Maven.MavenInstallation;

/**
 * @author Alexey Merezhin
 */
public class FlowTriggerStepTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @ClassRule public static JenkinsRule j = new JenkinsRule();
    @ClassRule public static LoggerRule logging = new LoggerRule();
    private static MavenInstallation mvn;
    private static WorkflowJob pipeJob;

    /*
        Test project deps:
        parent_a needs child_a
        parent_b needs child_a and child_b
        grand needs parent_a and parent_b
     */
    @BeforeClass
    public static void setUp() throws Exception {
        mvn = configureMaven3();
        String[] projectList = new String[]{"child_a", "child_b", "parent_a", "parent_b", "grand"};
        for (String project : projectList) {
            MavenModuleSet job = createProject(project, mvn);
            // TODO what if job has never been built?
            j.buildAndAssertSuccess(job);
        }
        // j.jenkins.getDependencyGraph().build();

        pipeJob = j.jenkins.createProject(WorkflowJob.class, "pipe_test");
    }

    @Test
    public void actOnProjectWithoutDeps() throws Exception {
        int buildNumberChildA = getJob("child_a").getLastBuild().getNumber();

        pipeJob.setDefinition(new CpsFlowDefinition("flowexec \"child_a\"", true));
        j.assertLogContains("Scheduling project: child_a", j.buildAndAssertSuccess(pipeJob));

        j.assertBuildStatusSuccess(getJob("child_a").getBuildByNumber(buildNumberChildA+1));
    }

    @Test
    public void actOnProjectWithSingleDep() throws Exception {
        int buildNumberChildA = getJob("child_a").getLastBuild().getNumber();
        int buildNumberParentA = getJob("parent_a").getLastBuild().getNumber();

        pipeJob.setDefinition(new CpsFlowDefinition("flowexec \"parent_a\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(pipeJob);
        j.assertLogContains("Scheduling project: child_a", workflowRun);
        j.assertLogContains("Scheduling project: parent_a", workflowRun);

        j.assertBuildStatusSuccess(getJob("child_a").getBuildByNumber(buildNumberChildA+1));
        j.assertBuildStatusSuccess(getJob("parent_a").getBuildByNumber(buildNumberParentA+1));
    }

    @Test
    public void actOnProjectWithMultiDeps() throws Exception {
        pipeJob.setDefinition(new CpsFlowDefinition("flowexec \"grand\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(pipeJob);
        j.assertLogContains("Scheduling project: child_a", workflowRun);
        j.assertLogContains("Scheduling project: child_b", workflowRun);
        j.assertLogContains("Scheduling project: parent_a", workflowRun);
        j.assertLogContains("Scheduling project: parent_b", workflowRun);
        j.assertLogContains("Scheduling project: grand", workflowRun);
    }

    @Test
    public void testDepCalculation() throws Exception {
        FlowTriggerStepExecution stepExecution = new FlowTriggerStepExecution();
        Set<AbstractProject> projects = new LinkedHashSet<>();
        stepExecution.createProjectList(getJob("grand"), projects);
        Set<AbstractProject> expectedList = new LinkedHashSet<>();
        expectedList.add(getJob("child_a"));
        expectedList.add(getJob("parent_a"));
        expectedList.add(getJob("child_b"));
        expectedList.add(getJob("parent_b"));
        expectedList.add(getJob("grand"));
        assertEquals(expectedList, projects);

    }

    public MavenModuleSet getJob(String jobName) {
        return j.getInstance().getItemByFullName(jobName, MavenModuleSet.class);
    }

    public static MavenModuleSet createProject(String resource, MavenInstallation mvn) throws IOException {
        MavenModuleSet project = j.createProject(MavenModuleSet.class, resource);
        project.setRootPOM(resource + "/pom.xml");
        project.setMaven(mvn.getName());
        project.setScm(new ExtractResourceSCM(FlowTriggerStepTest.class.getResource(resource + ".zip")));
        project.setGoals("clean install");

        /* keep maven files inside work folder with jenkins installation */
        project.setLocalRepository(new TestRepositoryLocator(mvn.getHomeDir()));

        return project;
    }

}