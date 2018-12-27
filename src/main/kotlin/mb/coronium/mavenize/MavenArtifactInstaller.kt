package mb.coronium.mavenize

import mb.coronium.model.maven.InstallableMavenArtifact
import mb.coronium.util.Log
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.installation.InstallRequest
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.util.artifact.SubArtifact
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Installs Maven artifacts into [repoDir].
 */
class MavenArtifactInstaller(repoDir: Path) {
  private val system: RepositorySystem
  private val session: RepositorySystemSession

  init {
    if(!Files.exists(repoDir)) {
      Files.createDirectories(repoDir)
    } else if(!Files.isDirectory(repoDir)) {
      throw IOException("Repository at path $repoDir is not a directory")
    }

    val locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
    locator.addService(TransporterFactory::class.java, FileTransporterFactory::class.java)

    // Uncomment for deployment over HTTP.
    //locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)

    // Uncomment for routing service creation error messages somewhere.
    //locator.setErrorHandler(object : DefaultServiceLocator.ErrorHandler() {
    //  fun serviceCreationFailed(type: Class<*>, impl: Class<*>, exception: Throwable) {
    //    LOGGER.error("Service creation failed for {} implementation {}: {}",
    //      type, impl, exception.message, exception)
    //  }
    //})

    system = locator.getService(RepositorySystem::class.java)
    session = MavenRepositorySystemUtils.newSession()
    val localRepo = LocalRepository(repoDir.toAbsolutePath().toString())
    session.localRepositoryManager = system.newLocalRepositoryManager(session, localRepo)

    // Uncomment for routing logging messages regarding transfer progress and repositories somewhere.
    //session.transferListener = ConsoleTransferListener()
    //session.repositoryListener = ConsoleRepositoryListener()
  }


  fun install(artifact: InstallableMavenArtifact, log: Log) {
    val installRequest = InstallRequest()
    installRequest.requestInstallOf(artifact)
    log.progress("Executing installation request")
    system.install(session, installRequest)
  }

  fun installAll(artifacts: Iterable<InstallableMavenArtifact>, log: Log) {
    val installRequest = InstallRequest()
    artifacts.forEach { artifact ->
      installRequest.requestInstallOf(artifact)
    }
    log.progress("Executing installation request")
    system.install(session, installRequest)
  }

  private fun InstallRequest.requestInstallOf(installableMavenArtifact: InstallableMavenArtifact) {
    val artifact: Artifact = run {
      val primaryArtifact = installableMavenArtifact.primaryArtifact
      val coords = primaryArtifact.coordinates
      DefaultArtifact(coords.groupId, coords.id, coords.classifier, coords.extension, coords.version.toString(), null, primaryArtifact.file.toFile())
    }
    addArtifact(artifact)

    for(subArtifact in installableMavenArtifact.subArtifacts) {
      addArtifact(SubArtifact(artifact, subArtifact.classifier, subArtifact.extension, subArtifact.file.toFile()))
    }
  }
}