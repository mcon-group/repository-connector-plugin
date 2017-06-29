package org.jvnet.hudson.plugins.artifactdownloader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.plugins.repositoryconnector.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;

/**
 * This builder allows to resolve artifacts from a repository and copy it to any location.
 * 
 * @author domi
 */
public class ArtifactDeployer extends Notifier implements Serializable {

    private static final long serialVersionUID = 1L;

    static Logger log = Logger.getLogger(ArtifactDeployer.class.getName());

    public final UserPwd overwriteSecurity;
    public boolean enableRepoLogging = true;
    public final String repoId;
    public final String snapshotRepoId;
    public List<ArtifactConfig> artifacts;

    @DataBoundConstructor
    public ArtifactDeployer(List<ArtifactConfig> artifacts, String repoId, String snapshotRepoId, UserPwd overwriteSecurity, boolean enableRepoLogging) {
        this.enableRepoLogging = enableRepoLogging;
        this.artifacts = artifacts != null ? artifacts : new ArrayList<ArtifactConfig>();
        this.repoId = repoId;
        this.snapshotRepoId = snapshotRepoId;
        this.overwriteSecurity = overwriteSecurity;
        System.out.println(this.overwriteSecurity);
    }

    public boolean enableRepoLogging() {
        return enableRepoLogging;
    }

    public boolean isOverwriteSecurity() {
        return overwriteSecurity != null;
    }

    public String getUserName() {
        if (isOverwriteSecurity()) {
            return overwriteSecurity.user;
        }
        return null;
    }

    public String getPassword() {
        if (isOverwriteSecurity()) {
            return overwriteSecurity.password;
        }
        return null;
    }

    public Collection<RepositoryConfig> getRepos() {
        return RepositoryConfiguration.get().getRepos();
    }

    private RepositoryConfig getRepoById(String id) {
        return RepositoryConfiguration.get().getRepositoryMap().get(id);
    }

    /*
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {

        final PrintStream logger = listener.getLogger();

        try {
            for (ArtifactConfig a : artifacts) {
            	/**
                final String version = TokenMacro.expandAll(build, listener, a.getVersion());
                final String classifier = TokenMacro.expandAll(build, listener, a.getClassifier());
                final String artifactId = TokenMacro.expandAll(build, listener, a.getArtifactId());
                final String groupId = TokenMacro.expandAll(build, listener, a.getGroupId());
                final String packaging = TokenMacro.expandAll(build, listener, a.getExtension());
                final String targetFileName = TokenMacro.expandAll(build, listener, a.getTargetFileName());
                **/
            }
        } catch (Exception e) {
            return logError("Exception: ", logger, e);
        }
        return true;
    }

    private boolean logError(String msg, final PrintStream logger, Exception e) {
        log.log(Level.SEVERE, msg, e);
        logger.println(msg);
        e.printStackTrace(logger);
        return false;
    }

    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // TODO: This disables the extension => change to true
            return false;
        }

        public String getDisplayName() {
            return Messages.ArtifactDeployer();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return true;
        }
    }

    private File getTempPom(ArtifactConfig artifact) {
        File tmpPom = null;
        try {
            final String preparedPom = this.preparedPom(artifact);
            tmpPom = File.createTempFile("pom" + artifact.getArtifactId(), ".xml");
            FileUtils.writeStringToFile(tmpPom, preparedPom);
        } catch (IOException e) {
            log.log(Level.SEVERE, "not able to create temporal pom: " + e.getMessage());
        }
        return tmpPom;
    }

    private String preparedPom(ArtifactConfig artifact) {
        String pomContent = null;
        try {
            final InputStream stream = this.getClass().getResourceAsStream("/org/jvnet/hudson/plugins/repositoryconnector/ArtifactDeployer/pom.tmpl");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
            StringBuilder stringBuilder = new StringBuilder();
            String line = null;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            pomContent = stringBuilder.toString();
            pomContent = pomContent.replace("ARTIFACTID", artifact.getArtifactId());
            pomContent = pomContent.replace("GROUPID", artifact.getGroupId());
            pomContent = pomContent.replace("VERSION", artifact.getVersion());
            // FIXME how to handle packaging vs extension?
            pomContent = pomContent.replace("PACKAGING", artifact.getExtension());

        } catch (Exception e) {
            log.log(Level.SEVERE, "not able to create temporal pom: " + e.getMessage());
        }
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "used POM: " + pomContent);
        }
        return pomContent;
    }
}
