package org.jenkinsci.plugins.workflow.dependency.walker;

import static junit.framework.TestCase.assertEquals;
import static org.jvnet.hudson.test.ToolInstallations.configureMaven3;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.dependency.walker.helpers.TestRepositoryLocator;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Maven.MavenInstallation;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;

/**
 * @author Alexey Merezhin
 */
public class FlowTriggerStepTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @ClassRule public static JenkinsRule j = new JenkinsRule();
    @ClassRule public static LoggerRule logging = new LoggerRule();
    private static WorkflowJob pipeJob;
    /*
        Dependencies of test projects:
        parent_a needs child_a
        parent_b needs child_a and child_b
        grand needs parent_a and parent_b
     */
    private static final String[] TEST_PROJECTS = new String[]{"child_a", "child_b", "parent_a", "parent_b", "grand"};

    @BeforeClass
    public static void setUpClass() throws Exception {
        MavenInstallation mvn = configureMaven3();
        for (String project : TEST_PROJECTS) {
            MavenModuleSet job = createProject(project, mvn);
            job.setGoals("clean");
            // TODO what if job has never been built?
            j.buildAndAssertSuccess(job);
        }
        // TODO how to build dep-graph without building jobs?
        Jenkins.getInstance().rebuildDependencyGraph();

        pipeJob = j.jenkins.createProject(WorkflowJob.class, "pipe_test");
    }

    @Before
    public void setUp() throws Exception {
        for (String project : TEST_PROJECTS) {
            getJob(project).setGoals("clean install");
        }
    }

    private static MavenModuleSet getJob(String jobName) {
        return j.getInstance().getItemByFullName(jobName, MavenModuleSet.class);
    }

    private static MavenModuleSet createProject(String resource, MavenInstallation mvn) throws IOException {
        MavenModuleSet project = j.createProject(MavenModuleSet.class, resource);
        project.setRootPOM(resource + "/pom.xml");
        project.setMaven(mvn.getName());
        project.setScm(new ExtractResourceSCM(FlowTriggerStepTest.class.getResource(resource + ".zip")));

        /* keep maven files inside work folder with jenkins installation */
        project.setLocalRepository(new TestRepositoryLocator(mvn.getHomeDir()));

        return project;
    }

    @Test
    public void actOnProjectWithoutDeps() throws Exception {
        int buildNumberChildA = getJob("child_a").getLastBuild().getNumber();

        pipeJob.setDefinition(new CpsFlowDefinition("walk \"child_a\"", true));
        j.assertLogContains("Scheduling project: child_a", j.buildAndAssertSuccess(pipeJob));

        j.assertBuildStatusSuccess(getJob("child_a").getBuildByNumber(buildNumberChildA+1));
    }

    @Test
    public void actOnProjectWithSingleDep() throws Exception {
        int buildNumberChildA = getJob("child_a").getLastBuild().getNumber();
        int buildNumberParentA = getJob("parent_a").getLastBuild().getNumber();

        pipeJob.setDefinition(new CpsFlowDefinition("walk \"parent_a\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(pipeJob);
        j.assertLogContains("Scheduling project: child_a", workflowRun);
        j.assertLogContains("Scheduling project: parent_a", workflowRun);

        j.assertBuildStatusSuccess(getJob("child_a").getBuildByNumber(buildNumberChildA+1));
        j.assertBuildStatusSuccess(getJob("parent_a").getBuildByNumber(buildNumberParentA+1));
    }

    @Test
    public void actOnProjectWithMultiDeps() throws Exception {
        pipeJob.setDefinition(new CpsFlowDefinition("walk \"grand\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(pipeJob);
        j.assertLogContains("Scheduling project: child_a", workflowRun);
        j.assertLogContains("Scheduling project: child_b", workflowRun);
        j.assertLogContains("Scheduling project: parent_a", workflowRun);
        j.assertLogContains("Scheduling project: parent_b", workflowRun);
        j.assertLogContains("Scheduling project: grand", workflowRun);
    }

    @Test
    public void testDepCalculation() throws Exception {
        WalkerStepExecution stepExecution = new WalkerStepExecution();
        Set<AbstractProject> projects = stepExecution.createProjectList(getJob("grand"));
        Set<AbstractProject> expectedList = new LinkedHashSet<>();
        expectedList.add(getJob("child_a"));
        expectedList.add(getJob("parent_a"));
        expectedList.add(getJob("child_b"));
        expectedList.add(getJob("parent_b"));
        expectedList.add(getJob("grand"));
        assertEquals(expectedList, projects);

    }

    @Test
    public void testCustomAction() throws Exception {
        int buildNumberChildA = getJob("child_a").getLastBuild().getNumber();

        pipeJob.setDefinition(new CpsFlowDefinition("walk job: \"child_a\", jobAction: \"echo 'JOB_NAME'\"", true));
        j.assertLogContains("echo 'child_a'", j.buildAndAssertSuccess(pipeJob));

        j.assertBuildStatusSuccess(getJob("child_a").getBuildByNumber(buildNumberChildA));
    }

    @Test
    public void failIfDependentJobFailed() throws Exception {
        getJob("child_a").setGoals("xxx");
        QueueTaskFuture build = (new ParameterizedJobMixIn() {
            protected Job asJob() {
                return getJob("child_a");
            }
        }).scheduleBuild2(0, new Action[0]);
        j.assertBuildStatus(Result.FAILURE, build);

        pipeJob.setDefinition(new CpsFlowDefinition("walk job: \"parent_a\", jobAction: \"echo 'JOB_NAME'\", failOnUnstable: true", true));
        build = (new ParameterizedJobMixIn() {
            protected Job asJob() {
                return pipeJob;
            }
        }).scheduleBuild2(0);
        j.assertBuildStatus(Result.FAILURE, build);

        pipeJob.setDefinition(new CpsFlowDefinition("walk job: \"parent_a\", jobAction: \"echo 'JOB_NAME'\", failOnUnstable: false", true));
        build = (new ParameterizedJobMixIn() {
            protected Job asJob() {
                return pipeJob;
            }
        }).scheduleBuild2(0);
        j.assertBuildStatus(Result.SUCCESS, build);
    }

}