package org.jvnet.hudson.plugins.artifactdownloader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.version.Version;
import org.jvnet.hudson.plugins.artifactdownloader.aether.RepositoryConnector;
import org.jvnet.hudson.plugins.artifactdownloader.Messages;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.export.Exported;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.util.FormValidation;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

public class VersionParameterDefinition extends SimpleParameterDefinition {

    private static final Logger log = Logger.getLogger(VersionParameterDefinition.class.getName());

    private final String groupid;
    private final String repoid;
    private final String artifactid;
    private final String propertyName;

    @DataBoundConstructor
    public VersionParameterDefinition(String repoid, String groupid, String artifactid, String propertyName, String description) {
        super(groupid + "." + artifactid, description);
        this.repoid = repoid;
        this.groupid = groupid;
        this.artifactid = artifactid;
        this.propertyName = propertyName;
    }

    @Override
    public VersionParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof StringParameterValue) {
            return new VersionParameterDefinition(getRepoid(), "", "", "", getDescription());
        } else {
            return this;
        }
    }

    private RepositoryConfig getRepoById(String id) {
        return RepositoryConfiguration.get().getRepositoryMap().get(id);
    }

    @Exported
    public List<String> getChoices() {
    	List<String> versionStrings = new ArrayList<String>();
    	RepositoryConnector rc = null;
    	try {

        	RepositoryConfig rConf = getRepoById(repoid);
        	if(rConf==null) {
        		throw new RuntimeException("invalid repo id: "+repoid);
        	}
    		
    		rc = new RepositoryConnector(System.out, Collections.singletonList(rConf));
            List<Version> versions = rc.listVersions(groupid, artifactid, null, null, null);
            for (Version version : versions) {
                versionStrings.add(version.toString());
            }
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Could not determine versions", ex);
            versionStrings.add("Could not determine versions ("+ex.getMessage()+")");

        } finally {
			IOUtils.closeQuietly(rc);
		}
    	if (!versionStrings.isEmpty()) {
    		Collections.reverse(versionStrings);
        } 
        return versionStrings;
    }

    @Exported
    public String getArtifactid() {
        return artifactid;
    }

    @Exported
    public String getRepoid() {
        return repoid;
    }

    @Exported
    public String getGroupid() {
        return groupid;
    }

    @Exported
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return new VersionParameterValue(groupid, artifactid, propertyName, jo.getString("value"));
    }

    @Override
    public ParameterValue createValue(String version) {
        // this should never be called
        throw new RuntimeException("Not implemented");
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DESCRIPTOR;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends ParameterDescriptor {

        public DescriptorImpl() {
            super(VersionParameterDefinition.class);
            load();
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
        public boolean configure(StaplerRequest req, JSONObject formData) {
            save();
            return true;
        }

        public FormValidation doCheckGroupid(@QueryParameter String groupid,
                @QueryParameter String artifactid,
                @QueryParameter String repoid) throws IOException {
            FormValidation result = FormValidation.ok();
            if (groupid == null || groupid.isEmpty()) {
                result = FormValidation.error(Messages.EmptyGroupId());
            } else {
                if (artifactid != null && !artifactid.isEmpty() && repoid != null && !repoid.isEmpty()) {
                    result = checkPath(artifactid, groupid, repoid);
                }
            }
            return result;
        }

        public FormValidation doCheckArtifactid(
                @QueryParameter String artifactid,
                @QueryParameter String groupid, @QueryParameter String repoid)
                throws IOException {
            FormValidation result = FormValidation.ok();
            if (artifactid == null || artifactid.isEmpty()) {
                result = FormValidation.error(Messages.EmptyArtifactId());
            } else {
                if (groupid != null && !groupid.isEmpty() && repoid != null && !repoid.isEmpty()) {
                    result = checkPath(artifactid, groupid, repoid);
                }
            }
            return result;
        }

        private FormValidation checkPath(String artifactid, String groupid,
                String repoid) {
            FormValidation result = FormValidation.ok();
            try {
            	RepositoryConfig r = DESCRIPTOR.getRepo(repoid);
            	RepositoryConnector rc = new RepositoryConnector(System.out, Collections.singletonList(r));
            	List<Version> versions = rc.listVersions(groupid, artifactid, null, null, null);
                if (versions.isEmpty()) {
                    result = FormValidation.error(Messages.NoVersions() + " " + groupid + "." + artifactid);
                    log.log(Level.FINE, "No versions found for " + groupid + "." + artifactid);
                }
            } catch (Exception ex) {
                result = FormValidation.error(Messages.NoVersions() + " " + groupid + "." + artifactid);
                log.log(Level.SEVERE, "Could not determine versions for " + groupid + "." + artifactid, ex);
            }
            return result;
        }

        public FormValidation doCheckRepoid(@QueryParameter String repoid)
                throws IOException {
            FormValidation result = FormValidation.ok();
            if (repoid == null || repoid.isEmpty()) {
                result = FormValidation.error(Messages.EmptyRepositoryName());
            }
            return result;
        }

        public FormValidation doCheckBaseurl(@QueryParameter String baseurl)
                throws IOException {
            FormValidation result = FormValidation.ok();
            if (baseurl == null || baseurl.isEmpty()) {
                result = FormValidation.error(Messages.EmptyBaseURL());
            }
            return result;
        }

        public FormValidation doCheckPassword(@QueryParameter String password,
                @QueryParameter String username) throws IOException {
            FormValidation result = FormValidation.ok();
            if (password != null && !password.isEmpty()
                    && (username == null || username.isEmpty())) {
                result = FormValidation.error(Messages.EmptyUsername());
            }
            return result;
        }

        public FormValidation doTestConnection(@QueryParameter String baseurl,
                @QueryParameter String username, @QueryParameter String password)
                throws IOException, ServletException {
            try {
                if (true /*MavenRepositoryClient.testConnection(baseurl, username,
                         password)*/) {
                    return FormValidation.ok(Messages.Success());
                } else {
                    return FormValidation.error(Messages.ConnectionFailed());
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Client error: " + e.getMessage(), e);
                return FormValidation.error(Messages.ClientError()
                        + e.getMessage());
            }
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(this.getClass().getSimpleName());
        sb.append("@[");
        sb.append("name=");
        sb.append(getName());
        sb.append(", description=");
        sb.append(getDescription());
        sb.append(", groupid=");
        sb.append(groupid);
        sb.append(", repoid=");
        sb.append(repoid);
        sb.append(", artifactid=");
        sb.append(artifactid);
        sb.append(']');
        return sb.toString();
    }
}
