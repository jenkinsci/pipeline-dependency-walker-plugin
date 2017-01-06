package org.jenkinsci.plugins.pipeline.dependency.flow;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.inject.Inject;

import groovy.lang.Script;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ModelHyperlinkNote;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

/**
 * @author Alexey Merezhin
 */
public class FlowTriggerStepExecution extends StepExecution {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(FlowTriggerStepExecution.class.getName());

    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient Run<?,?> invokingRun;

    @Inject private transient FlowTriggerStep step;

    @Override
    public boolean start() throws Exception {
        LOGGER.info("Start flow with root job: " + step.getJob());

        Jenkins jenkins = Jenkins.getActiveInstance();
        MavenModuleSet rootProject = jenkins.getItemByFullName(step.getJob(), MavenModuleSet.class);

        Set<AbstractProject> projects = new LinkedHashSet<>();
        createProjectList(rootProject, projects);

        /* Generate flow script */
        StringBuffer definition = new StringBuffer();
        for (AbstractProject project : projects) {
            listener.getLogger().println("Add job to the queue: " +
                    ModelHyperlinkNote.encodeTo("/" + project.getUrl(), project.getFullDisplayName()));
            definition.append("build \"" + project.getName() + "\"");
            definition.append("\n");
        }

        listener.getLogger().println("Generated script:\n");
        listener.getLogger().println(definition.toString());
        listener.getLogger().println();

        /* Execute generated script */
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();
        CpsFlowExecution execution = t.getExecution();

        Script script = execution.getShell().parse(definition.toString());

        // execute body as another thread that shares the same head as this thread
        // as the body can pause.
        cps.newBodyInvoker(t.getGroup().export(script))
           .withDisplayName("Dependency Flow Actions")
           .withCallback(BodyExecutionCallback.wrap(cps))
           .start(); // when the body is done, the flow step is done

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        // noop
        //
        // the head of the CPS thread that's executing the generated script should stop and that's all we need to do.
    }

    /**
     * Create a list of dependent projects, first element is in the bottom of dependency graph
     * @param project top project
     * @param projects resulting list
     */
    private void createProjectList(AbstractProject project, Set<AbstractProject> projects) {
        for (AbstractProject currentProject: getUpstream(project)) {
            createProjectList(currentProject, projects);
        }
        projects.add(project);
    }

    private List<AbstractProject> getUpstream(AbstractProject project) {
        Jenkins jenkins = Jenkins.getActiveInstance();
        // MavenModuleSet rootProject = jenkins.getItemByFullName(jobName, MavenModuleSet.class);
        DependencyGraph dependencyGraph = jenkins.getDependencyGraph();
        return dependencyGraph.getUpstream(project);
    }

}
