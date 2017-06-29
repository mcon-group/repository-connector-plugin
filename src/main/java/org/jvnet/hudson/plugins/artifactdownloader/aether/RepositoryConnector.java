package org.jvnet.hudson.plugins.artifactdownloader.aether;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.version.Version;
import org.jvnet.hudson.plugins.artifactdownloader.ArtifactConfig;
import org.jvnet.hudson.plugins.artifactdownloader.RepositoryConfig;

public class RepositoryConnector implements Closeable {

	private List<RepositoryConfig> repositoryConfigs = new ArrayList<RepositoryConfig>();
	private final PrintStream logger;

	private File tmpRepo;
	
	public RepositoryConnector(PrintStream logger, List<RepositoryConfig> repositoryConfigs) throws IOException {
		this.logger = logger == null ? System.out : logger;
		this.repositoryConfigs = repositoryConfigs;
		tmpRepo = File.createTempFile("local_repo_", "");
		if(!tmpRepo.delete()) {
			throw new IOException("failed to create tmp file: "+tmpRepo.getAbsolutePath());
		}
		if(!tmpRepo.mkdirs()) {
			throw new IOException("failed to create tmp file!");
		}
	}

	@Override
	public void close() throws IOException {
		FileUtils.deleteDirectory(tmpRepo);
	}
	
	private RepositorySystem createRepoSystem() {
		DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
		locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
		locator.addService(TransporterFactory.class, FileTransporterFactory.class);
		locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
		locator.addService(VersionResolver.class, DefaultVersionResolver.class);
		locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
			@Override
			public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
				logger.println("error creating repository service");
				exception.printStackTrace(logger);
			}
		});
		RepositorySystem out = locator.getService(RepositorySystem.class);
		if(out==null) {
			throw new RuntimeException("unable to create a repository system");
		}
		return out;
	}

	private RepositorySystemSession newSession(RepositorySystem system) throws IOException {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository localRepo = new LocalRepository(tmpRepo);
		session.setTransferListener(new TransferListener() {
			
			@Override
			public void transferSucceeded(TransferEvent arg0) {
				logger.println("transfer succeeded: "+arg0.getTransferredBytes()+"/"+arg0.getDataLength()+" --- "+arg0.getResource().getResourceName());
			}
			
			@Override
			public void transferStarted(TransferEvent arg0) throws TransferCancelledException {
				logger.println("transfer started: "+arg0.getTransferredBytes()+"/"+arg0.getDataLength()+" --- "+arg0.getResource().getResourceName());
			}
			
			@Override
			public void transferProgressed(TransferEvent arg0) throws TransferCancelledException {
			}
			
			@Override
			public void transferInitiated(TransferEvent arg0) throws TransferCancelledException {
				logger.println("staring transfer ... "+arg0.getResource().getResourceName());
			}
			
			@Override
			public void transferFailed(TransferEvent arg0) {
				logger.println("staring failed! "+arg0.getResource().getResourceName());
			}
			
			@Override
			public void transferCorrupted(TransferEvent arg0) throws TransferCancelledException {
				// TODO Auto-generated method stub
				
			}
		});
		return session.setLocalRepositoryManager(
				system.newLocalRepositoryManager(
						session, 
						localRepo
				)
		);
	}

	private List<RemoteRepository> getRepositories() {
		List<RemoteRepository> out = new ArrayList<RemoteRepository>();
		for(RepositoryConfig conf : repositoryConfigs) {
			RemoteRepository.Builder builder = new RemoteRepository.Builder(
					conf.getId(),
					conf.getType(),
					conf.getUrl()
			);
			if(!StringUtils.isEmpty(conf.getUser())) {
				Authentication auth = new AuthenticationBuilder().addUsername(conf.getUser()).addPassword(conf.getPassword()).build();
				builder = builder.setAuthentication(auth);
			}
			out.add(builder.build());
		}
		return out;
	}

	public List<Version> listVersions(String groupId, String artifactId, String packaging, String classifier, String versionConstraints) throws VersionRangeResolutionException, IOException {
		RepositorySystem system = createRepoSystem();
		RepositorySystemSession session = newSession(system);

		Artifact artifact = new DefaultArtifact( groupId+":"+artifactId+":[0,)" );
		
		logger.println("resolving artigfact: "+artifact);
		
		VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact( artifact );

        List<RemoteRepository> repos = getRepositories();
        for(RemoteRepository rr : repos) {
    		logger.println(" - using repository: "+rr.getUrl());
        }
        
        rangeRequest.setRepositories(repos);

        VersionRangeResult rangeResult = system.resolveVersionRange( session, rangeRequest );
		logger.println("versions found: "+rangeResult.getVersions().size());
		return rangeResult.getVersions();
	}

	public boolean downloadArtifacts(List<ArtifactConfig> artifacts) throws VersionRangeResolutionException, IOException, ArtifactResolutionException {
		RepositorySystem system = createRepoSystem();
		RepositorySystemSession session = newSession(system);

		List<RemoteRepository> remotes = getRepositories();
		
		boolean out = true;
		
		for(ArtifactConfig ac : artifacts) {
			logger.println("downloading artifact: "+ac);
			
			FileInputStream fisIn = null;
			FileOutputStream fosOut = null;
			try {
				ArtifactRequest request = new ArtifactRequest();
				request.setArtifact(new DefaultArtifact(ac.getGroupId(),ac.getArtifactId(),ac.getExtension(),ac.getVersion()));
				request.setRepositories(remotes);
				ArtifactResult result = system.resolveArtifact(session, request);

				File fIn = result.getArtifact().getFile(); 
				File fOut = new File(ac.getTargetFileName());
				
				logger.println("copy artifact: "+fIn.getAbsolutePath()+" -> "+fOut.getAbsolutePath());
				
				fisIn = new FileInputStream(fIn);
				fosOut = new FileOutputStream(fOut);
				IOUtils.copy(fisIn,fosOut);
			} catch (Exception e) {
				logger.println("error copying file: "+e.getMessage());
				e.printStackTrace(logger);
				out = false;
			} finally {
				try {fisIn.close();} catch (Exception e2) { logger.println("unable to close file! (ignoring)");}
				try {fosOut.close();} catch (Exception e2) {logger.println("unable to close file! (ignoring)");}
			}
		}
		return out;
	}

	
	
	
	
}
