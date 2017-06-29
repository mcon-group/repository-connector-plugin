package org.jvnet.hudson.plugins.artifactdownloader;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.plugins.repositoryconnector.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

/**
 * This builder allows to resolve artifacts from a repository and copy it to any location.
 * 
 * @author domi
 */
public class ArtifactResolver extends Builder implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(ArtifactResolver.class.getName());

    private static final String DEFAULT_TARGET = "";

    public String targetDirectory;
    public List<ArtifactConfig> artifacts;
    public boolean failOnError = true;
    public boolean enableRepoLogging = true;
    public String snapshotUpdatePolicy;
    public String releaseUpdatePolicy;
    public String snapshotChecksumPolicy;
    public String releaseChecksumPolicy;
    
    @DataBoundConstructor
    public ArtifactResolver(
    		String targetDirectory, 
    		List<ArtifactConfig> artifacts, 
    		boolean failOnError, 
    		boolean enableRepoLogging, 
    		String snapshotUpdatePolicy,
            String snapshotChecksumPolicy, 
            String releaseUpdatePolicy, 
            String releaseChecksumPolicy) {
    	/**
        this.artifacts = artifacts != null ? artifacts : new ArrayList<Artifact>();
        this.targetDirectory = StringUtils.isBlank(targetDirectory) ? DEFAULT_TARGET : targetDirectory;
        this.failOnError = failOnError;
        this.enableRepoLogging = enableRepoLogging;
        this.releaseUpdatePolicy = releaseUpdatePolicy;
        this.releaseChecksumPolicy = RepositoryPolicy.CHECKSUM_POLICY_WARN;
        this.snapshotUpdatePolicy = snapshotUpdatePolicy;
        this.snapshotChecksumPolicy = RepositoryPolicy.CHECKSUM_POLICY_WARN;
        **/
    }

    public String getTargetDirectory() {
        return StringUtils.isBlank(targetDirectory) ? DEFAULT_TARGET : targetDirectory;
    }

    public boolean failOnError() {
        return failOnError;
    }

    public boolean enableRepoLogging() {
        return enableRepoLogging;
    }

    /**
     * gets the artifacts
     * 
     * @return
     */
    public List<ArtifactConfig> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

        final PrintStream logger = listener.getLogger();
        final Collection<RepositoryConfig> repositories = RepositoryConfiguration.get().getRepos();

        File localRepo = RepositoryConfiguration.get().getLocalRepoPath();
        boolean failed = download(build, listener, logger, repositories, localRepo);

        if (failed && failOnError) {
            return false;
        }
        return true;
    }

    private boolean download(AbstractBuild<?, ?> build, BuildListener listener, final PrintStream logger, final Collection<RepositoryConfig> repositories, File localRepository) {

    	boolean hasError = false;
        for (ArtifactConfig a : artifacts) {
        	/**
            try {

                final String classifier = TokenMacro.expandAll(build, listener, a.getClassifier());
                final String artifactId = TokenMacro.expandAll(build, listener, a.getArtifactId());
                final String groupId = TokenMacro.expandAll(build, listener, a.getGroupId());
                final String extension = TokenMacro.expandAll(build, listener, a.getExtension());
                final String targetFileName = TokenMacro.expandAll(build, listener, a.getTargetFileName());
                final String expandedTargetDirectory = TokenMacro.expandAll(build, listener, getTargetDirectory());

                String version = TokenMacro.expandAll(build, listener, a.getVersion());
                version = checkVersionOverride(build, listener, groupId, artifactId, version);

            } catch (Exception e) {
                hasError = logError("failed to expand tokens for " + a, logger, e);
            }
			**/
        }
        return hasError;
    }

    /**
     * This method searches for a build parameter of type VersionParameterValue and
     * substitutes the configured version by the one, defined by the parameter.
     *
     * @param build the build
     * @param listener the build listener
     * @param groupId the Maven group id
     * @param artifactId the Maven artifact id
     * @param version the version
     * @return The overridden version
     */
    private String checkVersionOverride(AbstractBuild<?, ?> build, BuildListener listener, String groupId, String artifactId, String version) {
        String result = version;
        List<ParametersAction> parameterActionList = build.getActions(ParametersAction.class);
        for (ParametersAction parameterAction : parameterActionList) {
            List<ParameterValue> parameterValueList = parameterAction.getParameters();
            for (ParameterValue parameterValue : parameterValueList) {
                if (parameterValue instanceof VersionParameterValue) {
                    VersionParameterValue versionParameterValue = (VersionParameterValue) parameterValue;
                    if (groupId != null && groupId.equals(versionParameterValue.getGroupid()) &&
                            artifactId != null && artifactId.equals(versionParameterValue.getArtifactid())) {
                        listener.getLogger().println("Overriding configured version '" + version + "' with version '"
                                + versionParameterValue.value + "' from build parameter");
                        result = versionParameterValue.value;
                    }
                }
            }
        }
        return result;
    }

    private boolean logError(String msg, final PrintStream logger, Exception e) {
        log.log(Level.SEVERE, msg, e);
        logger.println(msg);
        e.printStackTrace(logger);
        return true;
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return Messages.ArtifactResolver();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            return true;
        }
    }
}
