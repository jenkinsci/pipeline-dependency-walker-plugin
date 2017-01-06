package org.jenkinsci.plugins.pipeline.dependency.flow;

import static org.jvnet.hudson.test.ToolInstallations.configureMaven3;

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

/**
 * Created by e3cmea on 1/6/17.
 *
 * @author Alexey Merezhin
 */
public class FlowTriggerStepTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();
    private MavenModuleSet ds;

    @Before
    public void setUp() throws Exception {
        ds = j.createProject(MavenModuleSet.class, "ds");
        ds.setRootPOM("maven3-project/pom.xml");
        ds.setMaven(configureMaven3().getName());
        ds.setScm(new ExtractResourceSCM(getClass().getResource("maven3-project.zip")));
        ds.setGoals("install"); // build would fail with this goal
    }

    @Test
    public void actOnSingleProject() throws Exception {
        WorkflowJob us = j.jenkins.createProject(WorkflowJob.class, "us");
        us.setDefinition(new CpsFlowDefinition(FlowTriggerStep.STEP_NAME + " \"ds\"", true));
        WorkflowRun workflowRun = j.buildAndAssertSuccess(us);
    }

}