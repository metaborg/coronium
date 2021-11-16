@file:Suppress("UnstableApiUsage")

package mb.coronium.plugin.internal

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.mavenize.mavenizeEclipseInstallation
import mb.coronium.util.Arch
import mb.coronium.util.GradleLog
import mb.coronium.util.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*
import java.nio.file.Path
import java.nio.file.Paths

open class MavenizeExtension(project: Project) {
  val os: Property<Os> = project.objects.property()
  val arch: Property<Arch> = project.objects.property()
  val mirrorUrl: Property<String> = project.objects.property()
  val majorVersion: Property<String> = project.objects.property()
  val minorVersion: Property<String> = project.objects.property()

  init {
    os.convention(Os.current())
    arch.convention(Arch.current())
    mirrorUrl.convention("https://artifacts.metaborg.org/content/repositories/releases/org/eclipse")
    majorVersion.convention("2020-06")
    minorVersion.convention("R")
  }

  internal val version: Provider<String> = majorVersion.flatMap { majorVersion -> minorVersion.map { minorVersion -> "$majorVersion-$minorVersion" } }
  internal val groupId: Provider<String> = version.map { version -> "eclipse-$version"  }
  internal val prefixUrl: Provider<String> = version.map { version -> "eclipse-committers/$version/eclipse-committers-$version" }
  internal val url: Provider<String> = mirrorUrl.flatMap { mirrorUrl -> prefixUrl.flatMap { prefixUrl -> os.flatMap { os -> arch.map { arch -> "$mirrorUrl/$prefixUrl-${os.eclipseDownloadUrlArchiveSuffix}${arch.appDownloadUrlArchiveSuffix}.${os.eclipseDownloadUrlArchiveExtension}" } } } }

  internal fun finalizeOsArch() {
    os.finalizeValue()
    arch.finalizeValue()
  }

  internal fun finalizeMirrorUrl() {
    mirrorUrl.finalizeValue()
  }

  internal fun finalizeVersion() {
    majorVersion.finalizeValue()
    minorVersion.finalizeValue()
  }

  internal fun finalize() {
    finalizeOsArch()
    finalizeMirrorUrl()
    finalizeVersion()
  }
}

class MavenizePlugin : Plugin<Project> {
  companion object {
    const val lazilyMavenizedExtraName = "lazily_mavenized"
    val mavenizeDir: Path = Paths.get(System.getProperty("user.home"), ".mavenize")
    val repoDir: Path = mavenizeDir.resolve("repo")
  }


  override fun apply(project: Project) {
    val extension = MavenizeExtension(project)
    project.extensions.add("mavenize", extension)

    @Suppress("UnstableApiUsage")
    project.repositories {
      maven {
        name = "mavenized"
        setUrl(MavenizePlugin.repoDir)
        // This repository only resolves to the groupId of the mavenized Eclipse instances.
        content {
          includeGroupByRegex("eclipse-.+")
        }
        // Make Gradle look at directory structure when Gradle metadata is enabled, as per: https://github.com/gradle/gradle/issues/11321#issuecomment-552894258
        metadataSources {
          mavenPom()
          artifact()
        }
      }
    }
  }
}

internal fun Project.mavenizeExtension() = project.extensions.getByType<MavenizeExtension>()

internal fun Project.lazilyMavenize(): MavenizedEclipseInstallation {
  if(project.extra.has(MavenizePlugin.lazilyMavenizedExtraName)) {
    return project.extra[MavenizePlugin.lazilyMavenizedExtraName] as MavenizedEclipseInstallation
  }

  val extension = project.mavenizeExtension()
  extension.finalize()
  val mavenized = mavenizeEclipseInstallation(
    MavenizePlugin.mavenizeDir,
    extension.url.get(),
    extension.os.get().eclipsePluginsDir,
    extension.os.get().eclipseConfigurationDir,
    extension.groupId.get(),
    GradleLog(project.logger)
  )
  project.extra.set(MavenizePlugin.lazilyMavenizedExtraName, mavenized)
  return mavenized
}
