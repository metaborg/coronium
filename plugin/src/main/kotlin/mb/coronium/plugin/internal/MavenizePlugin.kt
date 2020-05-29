package mb.coronium.plugin.internal

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.mavenize.mavenizeEclipseInstallation
import mb.coronium.util.GradleLog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

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

    @Suppress("UnstableApiUsage")
    project.repositories {
      maven {
        name = "mavenized"
        setUrl(mavenized.repoDir)
        // This repository only resolves to the groupId of the mavenized Eclipse instance.
        content {
          includeGroup(extension.groupId)
        }
        // Make Gradle look at directory structure when Gradle metadata is enabled, as per: https://github.com/gradle/gradle/issues/11321#issuecomment-552894258
        metadataSources {
          mavenPom()
          artifact()
        }
      }
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
