package org.jenkinsci.plugins.workflow.dependency.walker.helpers;

import java.io.File;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.FilePath;
import hudson.maven.AbstractMavenBuild;
import hudson.maven.local_repo.LocalRepositoryLocator;
import hudson.maven.local_repo.LocalRepositoryLocatorDescriptor;

/**
 * @author Alexey Merezhin
 */
public class TestRepositoryLocator extends LocalRepositoryLocator {
    private File path;

    @DataBoundConstructor
    public TestRepositoryLocator(File path) {
        this.path = path;
    }

    @Override
    public FilePath locate(AbstractMavenBuild abstractMavenBuild) {
        FilePath rootPath = new FilePath(path);
        return rootPath.child("maven-repository");
    }

    @Extension
    public static class DescriptorImpl extends LocalRepositoryLocatorDescriptor {
        public DescriptorImpl() {
        }

        public String getDisplayName() {
            return "In work folder";
        }
    }
}
