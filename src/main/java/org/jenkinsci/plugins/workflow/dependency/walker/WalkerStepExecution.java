package org.jenkinsci.plugins.workflow.dependency.walker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.inject.Inject;

import groovy.lang.Script;
import hudson.AbortException;
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
public class WalkerStepExecution extends StepExecution {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(WalkerStepExecution.class.getName());

    @StepContextParameter private transient TaskListener listener;
    @StepContextParameter private transient Run<?,?> invokingRun;

    @Inject private transient WalkerStep step;

    @Override
    public boolean start() throws Exception {
        LOGGER.info("Start walking from root job: " + step.getJob());

        Jenkins jenkins = Jenkins.getActiveInstance();
        MavenModuleSet rootProject = jenkins.getItemByFullName(step.getJob(), MavenModuleSet.class);
        Set<AbstractProject> projectList = createProjectList(rootProject);

        if (step.getFailOnUnstable()) {
            checkProjectStatus(projectList);
        }

        String actionScript = generateActionScript(projectList);
        listener.getLogger().println("Generated script:\n");
        listener.getLogger().println(actionScript);
        listener.getLogger().println();

        /* Execute generated script */
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();
        CpsFlowExecution execution = t.getExecution();

        Script script = execution.getShell().parse(actionScript);

        // execute body as another thread that shares the same head as this thread as the body can pause.
        cps.newBodyInvoker(t.getGroup().export(script))
           .withDisplayName("Dependency Walker Actions")
           .withCallback(BodyExecutionCallback.wrap(cps))
           .start(); // when the body is done, the flow step is done

        return false;
    }

    private void checkProjectStatus(Set<AbstractProject> projectList) throws AbortException {
        boolean abort = false;
        for (AbstractProject project : projectList) {
            if (project.getLastSuccessfulBuild() == null) {
                listener.getLogger().println("Project " + project.getName() + " has never been successfully built.");
            }
            if (project.getLastUnsuccessfulBuild() != null &&
                    project.getLastSuccessfulBuild().getNumber() < project.getLastUnsuccessfulBuild().getNumber()) {
                listener.getLogger().println("Project " + project.getName() + " has recently failed.");
                abort = true;
            }
        }
        if (abort) {
            throw new AbortException("Not all the projects have latest build successful.");
        }
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        // noop
        //
        // the head of the CPS thread that's executing the generated script should stop and that's all we need to do.
    }

    protected String generateActionScript(Set<AbstractProject> projects) {
        /* Generate flow script */
        StringBuffer definition = new StringBuffer();
        for (AbstractProject project : projects) {
            listener.getLogger().println("Add job to the queue: " +
                    ModelHyperlinkNote.encodeTo("/" + project.getUrl(), project.getFullDisplayName()));
            String action = step.getJobAction().replaceAll("JOB_NAME", project.getName());
            definition.append(action);
            definition.append("\n");
        }

        return definition.toString();
    }

    /**
     * Create a list of dependent projects, first element is in the bottom of dependency graph
     *
     * @param rootProject top project
     * @return list containing project itself and all its upstream projects
     */
    public Set<AbstractProject> createProjectList(MavenModuleSet rootProject) {
        Set<AbstractProject> projects = new LinkedHashSet<>();
        createProjectList(rootProject, projects);
        return projects;
    }

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
