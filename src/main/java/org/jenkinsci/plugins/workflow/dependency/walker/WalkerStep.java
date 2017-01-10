package org.jenkinsci.plugins.workflow.dependency.walker;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.maven.MavenModuleSet;
import hudson.model.AutoCompletionCandidates;
import hudson.model.ItemGroup;
import hudson.model.Job;

/**
 * @author Alexey Merezhin
 */
public class WalkerStep extends AbstractStepImpl {
    public static final String STEP_NAME = "walk";
    private String job;
    private String jobAction = "build JOB_NAME";
    private Boolean failOnUnstable = false;

    @DataBoundConstructor
    public WalkerStep(String job) {
        this.job = job;
    }

    public String getJob() {
        return job;
    }

    public String getJobAction() {
        return jobAction;
    }

    @DataBoundSetter public void setJobAction(String jobAction) {
        this.jobAction = jobAction;
    }

    public Boolean getFailOnUnstable() {
        return failOnUnstable;
    }

    @DataBoundSetter public void setFailOnUnstable(Boolean failOnUnstable) {
        this.failOnUnstable = failOnUnstable;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(WalkerStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return STEP_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Execute a pipeline task for the job and all its downstream jobs.";
        }

        public AutoCompletionCandidates doAutoCompleteJob(@AncestorInPath ItemGroup<?> context,
                @QueryParameter String value) {
            return AutoCompletionCandidates.ofJobNames(MavenModuleSet.class, value, context);
        }

        @Restricted(DoNotUse.class) // for use from config.jelly
        public String getContext() {
            Job<?,?> job = StaplerReferer.findItemFromRequest(Job.class);
            return job != null ? job.getFullName() : null;
        }

    }

}
