package mb.coronium.plugin.internal

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.mavenize.mavenizeEclipseInstallation
import mb.coronium.plugin.mavenizeExtension
import mb.coronium.util.GradleLog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.closureOf
import org.gradle.kotlin.dsl.extra

class MavenizePlugin : Plugin<Project> {
  companion object {
    const val mavenizedEclipseInstallationExtraName = "mavenized_eclipse_installation"
  }

  override fun apply(project: Project) {
    // HACK: eagerly download and Mavenize bundles from Eclipse archive, as they must be available for dependency
    // resolution, which may or may not happen in the configuration phase. This costs at least one HTTP request per
    // configuration phase, to check if we need to download and Mavenize a new Eclipse archive.
    val log = GradleLog(project.logger)
    val extension = project.mavenizeExtension()
    val mavenized = mavenizeEclipseInstallation(
      extension.mavenizeDir,
      extension.url,
      extension.os.pluginsDir,
      extension.os.configurationDir,
      extension.groupId,
      log
    )

    // Add Mavenized repository to project repositories.
    // HACK: get instance of BaseRepositoryFactory so that we can manually add a custom Maven repository.
    // From: https://discuss.gradle.org/t/how-can-i-get-hold-of-the-gradle-instance-of-the-repository-factory/6943/6
    project.run {
      val repositoryFactory = (this as ProjectInternal).services.get(BaseRepositoryFactory::class.java)
      this.repositories(closureOf<RepositoryHandler> {
        val mavenRepo = repositoryFactory.createMavenRepository()
        mavenRepo.name = "mavenized"
        mavenRepo.setUrl(mavenized.repoDir)
        // Add to top of repositories to speed up dependency resolution.
        addFirst(mavenRepo)
      })
    }

    project.extra.set(mavenizedEclipseInstallationExtraName, mavenized)
  }
}

fun Project.mavenizedEclipseInstallation(): MavenizedEclipseInstallation {
  if(!project.extra.has(MavenizePlugin.mavenizedEclipseInstallationExtraName)) {
    error("Tried to get Mavenized Eclipse installation, before MavenizePlugin was applied")
  }
  return project.extra[MavenizePlugin.mavenizedEclipseInstallationExtraName] as MavenizedEclipseInstallation
}
