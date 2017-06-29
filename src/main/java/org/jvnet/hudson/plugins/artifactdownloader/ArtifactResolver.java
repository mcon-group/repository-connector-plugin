package org.jvnet.hudson.plugins.artifactdownloader;

import java.io.File;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.jvnet.hudson.plugins.artifactdownloader.aether.RepositoryConnector;
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
 * @author domi, rm
 */
public class ArtifactResolver extends Builder implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(ArtifactResolver.class.getName());

    private static final String DEFAULT_TARGET = "";

    public String repoid;
    public String targetDirectory;
    public List<ArtifactConfig> artifacts;
    
    @DataBoundConstructor
    public ArtifactResolver(
    		String repoid, 
    		String targetDirectory, 
    		List<ArtifactConfig> artifacts
    	) {
        this.artifacts = artifacts != null ? artifacts : new ArrayList<ArtifactConfig>();
        this.targetDirectory = StringUtils.isBlank(targetDirectory) ? DEFAULT_TARGET : targetDirectory;
        this.repoid = repoid;
    }

    public String getTargetDirectory() {
        return StringUtils.isBlank(targetDirectory) ? DEFAULT_TARGET : targetDirectory;
    }

    public boolean failOnError() {
        return true;
    }

    public List<ArtifactConfig> getArtifacts() {
        return artifacts;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

        final PrintStream logger = listener.getLogger();

        RepositoryConnector rc = null;
        
        
        try {

        	RepositoryConfig rConf = getRepoById(repoid);
        	if(rConf==null) {
        		throw new RuntimeException("invalid repo id: "+repoid);
        	}
        	
        	rc = new RepositoryConnector(logger, Collections.singletonList(rConf));	
        	List<ArtifactConfig> resolvedArtifacts = new ArrayList<ArtifactConfig>();
        	
        	for(ArtifactConfig ac : getArtifacts()) {
        		
        		String groupId = TokenMacro.expandAll(build, listener, ac.getGroupId()); 
        		String artifactId = TokenMacro.expandAll(build, listener, ac.getArtifactId());
        		String version = checkVersionOverride(build, listener, groupId, artifactId, ac.getVersion());
        		String extension = TokenMacro.expandAll(build, listener, ac.getExtension());
        		
        		String targetDirectory = TokenMacro.expandAll(build, listener, getTargetDirectory());
        		if(StringUtils.isEmpty(targetDirectory)) {
        		} else if (!targetDirectory.endsWith("/")) {
        			targetDirectory = targetDirectory+"/";
        		}
        		
        		String targetFilename = new File(targetDirectory+TokenMacro.expandAll(build, listener, ac.getTargetFileName())).getAbsolutePath();
        		
        		ArtifactConfig acNew = new ArtifactConfig(groupId, artifactId, extension, version, extension, targetFilename);
                resolvedArtifacts.add(acNew);
                
        	}
        	
        	rc.downloadArtifacts(resolvedArtifacts);
        	return true;
        } catch (Exception e) {
            return logError("Exception: ", logger, e);
        } finally {
			IOUtils.closeQuietly(rc);
		}
    }

    private RepositoryConfig getRepoById(String id) {
        return RepositoryConfiguration.get().getRepositoryMap().get(id);
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

        public RepositoryConfig getRepo(String id) {
            RepositoryConfig repo = null;
            RepositoryConfiguration repoConfig = RepositoryConfiguration.get();
            if (repoConfig != null) {
                repo = repoConfig.getRepositoryMap().get(id);
                log.fine("getRepo(" + id + ")=" + repo);
            }
            return repo;
        }

        public Collection<RepositoryConfig> getRepos() {
            Collection<RepositoryConfig> repos = null;
            RepositoryConfiguration repoConfig = RepositoryConfiguration.get();
            if (repoConfig != null) {
                repos = repoConfig.getRepos();
                log.fine("getRepos()=" + repos);
            }
            return repos;
        }
        
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws Descriptor.FormException {
            save();
            return true;
        }
    }
}
