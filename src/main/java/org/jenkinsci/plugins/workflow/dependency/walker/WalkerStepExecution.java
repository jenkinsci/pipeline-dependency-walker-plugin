package org.jenkinsci.plugins.workflow.dependency.walker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.job.MvnGlobalSettingsProvider;
import org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider;
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
import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.DependencyGraph;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
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
        StringBuilder definition = new StringBuilder();
        for (AbstractProject project : projects) {
            listener.getLogger().println("Add job to the queue: " +
                    ModelHyperlinkNote.encodeTo("/" + project.getUrl(), project.getFullDisplayName()));

            String action = step.getJobAction();
            action = replaceJobConstants(project, action);
            definition.append(action);
            definition.append("\n");
        }

        return definition.toString();
    }

    /**
     * Function generates a project-specific script from generic by replacing all matches of constants:
     *   1. JOB_NAME
     *   2. JOB_SCM_URL
     *   3. JOB_SCM_BRANCH
     *   4. JOB_SCM_CREDINTIALS_ID
     *   5. POM_FILE
     *   6. MVN_SETTINGS
     *   7. MVN_GLOBAL_SETTINGS
     *
     * @param project where to get the values from
     * @param action generic script
     * @return project-specific script
     */
    public String replaceJobConstants(AbstractProject project, String action) {
        action = action.replaceAll("JOB_NAME", "'" + project.getName() + "'");

        if (project instanceof MavenModuleSet) {
            MavenModuleSet mvnProject = (MavenModuleSet) project;
            action = action.replaceAll("POM_FILE", "'" + mvnProject.getRootPOM(null) + "'");
            String mvnSettings = "";
            if (mvnProject.getSettings() instanceof MvnSettingsProvider) {
                mvnSettings = ((MvnSettingsProvider) mvnProject.getSettings()).getSettingsConfigId();
            }
            action = action.replaceAll("MVN_SETTINGS", "'" + mvnSettings + "'");
            String mvnGlobalSettings = "";
            if (mvnProject.getGlobalSettings() instanceof MvnGlobalSettingsProvider) {
                mvnGlobalSettings = ((MvnGlobalSettingsProvider) mvnProject.getGlobalSettings()).getSettingsConfigId();
            }
            action = action.replaceAll("MVN_GLOBAL_SETTINGS", "'" + mvnGlobalSettings + "'");
        }

        if (project.getScm() instanceof GitSCM) {
            GitSCM git = (GitSCM) project.getScm();
            String repo = "";
            if (!git.getRepositories().isEmpty() && !git.getRepositories().get(0).getURIs().isEmpty()) {
                repo = git.getRepositories().get(0).getURIs().get(0).toString();
            }
            String branch = "";
            String credentialsId = "";
            if (!"".equals(repo)) {
                if (!git.getBranches().isEmpty()) {
                    branch = git.getBranches().get(0).getName();
                    branch = branch.replace("*/", "");
                }
                if (!git.getUserRemoteConfigs().isEmpty()) {
                    credentialsId = git.getUserRemoteConfigs().get(0).getCredentialsId();
                }
            }
            action = action.replaceAll("JOB_SCM_URL", "'" + repo + "'")
                           .replaceAll("JOB_SCM_BRANCH", "'" + branch + "'")
                           .replaceAll("JOB_SCM_CREDINTIALS_ID", "'" + credentialsId + "'");
        }
        return action;
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
        DependencyGraph dependencyGraph = jenkins.getDependencyGraph();
        return dependencyGraph.getUpstream(project);
    }

}
