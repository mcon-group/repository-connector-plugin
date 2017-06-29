package org.jvnet.hudson.plugins.repositoryconnector.aether;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.version.Version;
import org.junit.Test;
import org.jvnet.hudson.plugins.artifactdownloader.ArtifactConfig;
import org.jvnet.hudson.plugins.artifactdownloader.RepositoryConfig;
import org.jvnet.hudson.plugins.artifactdownloader.aether.RepositoryConnector;

public class RepositoryConnectorTest {
	
	@Test
	public void testSimpleVersionResolution() throws IOException, VersionRangeResolutionException {
		RepositoryConfig rconf = new RepositoryConfig("central", "default", "http://repo1.maven.org/maven2", null, null, false);
		File f = File.createTempFile("repoconnector_unit", "");
		f.delete();
		f.mkdirs();
		
		RepositoryConnector rc = new RepositoryConnector(System.out, Collections.singletonList(rconf));
		List<Version> versions = rc.listVersions("org.apache.commons", "commons-lang3", "jar", null, null);
		System.out.println("versions found: "+versions.size());
		for(Version v : versions) {
			System.err.println(v.toString());
		}
	}

	@Test
	public void testSimpleArtifactResolution() throws IOException, VersionRangeResolutionException, ArtifactResolutionException {
		RepositoryConfig rconf = new RepositoryConfig("central", "default", "http://repo1.maven.org/maven2", null, null, false);
		File f = File.createTempFile("repoconnector_unit", "");
		f.delete();
		f.mkdirs();
		RepositoryConnector rc = new RepositoryConnector(System.out, Collections.singletonList(rconf));
		ArtifactConfig ac = new ArtifactConfig("org.apache.commons", "commons-lang3", "", "3.6", "jar", "./target/commons.jar");
		rc.downloadArtifacts(Collections.singletonList(ac));
	}
	
}
