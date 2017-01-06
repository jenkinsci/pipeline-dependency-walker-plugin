package org.jenkinsci.plugins.pipeline.dependency.flow;

import static org.jvnet.hudson.test.ToolInstallations.configureMaven3;

import java.io.IOException;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.maven.MavenModuleSet;
import hudson.tasks.Maven.MavenInstallation;

/**
 * Created by e3cmea on 1/6/17.
 *
 * @author Alexey Merezhin
 */
public class FlowTriggerStepTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();
    private MavenInstallation mvn;

    /*
        Test project deps:
        parent_a needs child_a
        parent_b needs child_a and child_b
        grand needs parent_a and parent_b
     */
    @Before
    public void setUp() throws Exception {
        mvn = configureMaven3();
        String[] projectList = new String[]{"child_a", "child_b", "parent_a", "parent_b", "grand"};
        for (String project : projectList) {
            createProject(project, mvn);
        }
    }

    @Test
    public void actOnSingleProject() throws Exception {
        WorkflowJob us = j.jenkins.createProject(WorkflowJob.class, "test");
        us.setDefinition(new CpsFlowDefinition(FlowTriggerStep.STEP_NAME + " \"child_a\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(us);



        Thread.sleep(1000000);
    }

    // TODO what if job has never been built

    public MavenModuleSet createProject(String resource, MavenInstallation mvn) throws IOException {
        MavenModuleSet project = j.createProject(MavenModuleSet.class, resource);
        project.setRootPOM(resource + "/pom.xml");
        project.setMaven(mvn.getName());
        project.setScm(new ExtractResourceSCM(getClass().getResource(resource + ".zip")));
        project.setGoals("clean install");
        return project;
    }
}