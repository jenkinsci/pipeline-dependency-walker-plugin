package org.jenkinsci.plugins.pipeline.dependency.flow;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.util.StaplerReferer;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.maven.MavenModuleSet;
import hudson.model.AutoCompletionCandidates;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.ParameterizedJobMixIn;

/**
 * Created by e3cmea on 1/6/17.
 *
 * @author Alexey Merezhin
 */
public class FlowTriggerStep extends AbstractStepImpl {
    private String job;

    @DataBoundConstructor
    public FlowTriggerStep(String job) {
        this.job = job;
    }

    public String getJob() {
        return job;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(FlowTriggerStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "flowexec";
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
