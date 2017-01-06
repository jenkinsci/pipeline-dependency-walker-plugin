package org.jenkinsci.plugins.pipeline.dependency.flow;

import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.google.inject.Inject;

import hudson.model.Run;
import hudson.model.TaskListener;

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
        getContext().onSuccess(null);
        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        getContext().onFailure(cause);
    }
}
