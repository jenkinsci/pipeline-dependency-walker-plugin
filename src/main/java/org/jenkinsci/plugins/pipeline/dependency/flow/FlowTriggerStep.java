package org.jenkinsci.plugins.pipeline.dependency.flow;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ModelHyperlinkNote;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.DependencyGraph;
import hudson.model.ItemGroup;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;

/**
 * Created by e3cmea on 1/6/17.
 *
 * @author Alexey Merezhin
 */
public class FlowTriggerStep extends Builder implements SimpleBuildStep {
    private static final Logger LOGGER = Logger.getLogger(FlowTriggerStep.class.getName());
    private @CheckForNull String job;

    @DataBoundConstructor
    public FlowTriggerStep(String job) {
        this.job = job;
    }

    public @Nonnull String getJob() {
        return job;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher,
            @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
        LOGGER.info("Start flow with root job: " + getJob());

        Jenkins jenkins = Jenkins.getActiveInstance();
        MavenModuleSet rootProject = jenkins.getItemByFullName(getJob(), MavenModuleSet.class);

        Set<AbstractProject> projects = new LinkedHashSet<>();
        createProjectList(rootProject, projects);

        for (AbstractProject project : projects) {
            taskListener.getLogger().println("Trigger job: " + ModelHyperlinkNote.encodeTo("/" + project.getUrl(), project.getFullDisplayName()));

            LOGGER.info("do it for project " + project.getName());
        }
    }

    /**
     * Create a list of dependent projects, first element is in the bottom of dependency graph
     * @param job top project's name
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

    @Symbol("flowexec")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> t) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Execute a pipeline task for the job and all its downstream jobs.";
        }

        public AutoCompletionCandidates doAutoCompleteJob(@AncestorInPath ItemGroup<?> context,
                @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(MavenModuleSet.class, value, context);
        }
    }

}
