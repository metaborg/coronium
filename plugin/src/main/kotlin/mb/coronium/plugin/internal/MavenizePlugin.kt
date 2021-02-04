@file:Suppress("UnstableApiUsage")

package mb.coronium.plugin.internal

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.mavenize.mavenizeEclipseInstallation
import mb.coronium.util.GradleLog
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.*
import java.nio.file.Path
import java.nio.file.Paths

enum class EclipseOs(
  val archiveSuffix: String,
  val archiveExtension: String,
  val pluginsDir: Path,
  val configurationDir: Path,
  val extraJvmArgs: List<String>
) {
  Windows(
    "win32",
    "zip",
    Paths.get("eclipse", "plugins"),
    Paths.get("eclipse", "configuration"),
    listOf()
  ),
  Linux(
    "linux-gtk",
    "tar.gz",
    Paths.get("eclipse", "plugins"),
    Paths.get("eclipse", "configuration"),
    listOf()
  ),
  OSX(
    "macosx-cocoa",
    "dmg",
    Paths.get("Eclipse.app", "Contents", "Eclipse", "plugins"),
    Paths.get("Eclipse.app", "Contents", "Eclipse", "configuration"),
    listOf("-XstartOnFirstThread")
  );

  companion object {
    fun current(): EclipseOs {
      val os = System.getProperty("os.name")
      return when {
        os.substring(0, 5).equals("linux", true) -> Linux
        os.substring(0, 7).equals("windows", true) -> Windows
        os.equals("Mac OS X", true) -> OSX
        else -> error("Unsupported Eclipse OS '$os'")
      }
    }
  }
}

enum class EclipseArch(val archiveSuffix: String) {
  X86_32(""),
  X86_64("-x86_64");

  companion object {
    fun current(): EclipseArch {
      val arch = System.getProperty("os.arch")
      return when(arch) {
        "x86", "i386" -> X86_32
        "amd64", "x86_64" -> X86_64
        else -> error("Unsupported Eclipse architecture '$arch'")
      }
    }
  }
}

open class MavenizeExtension(project: Project) {
  val os: Property<EclipseOs> = project.objects.property()
  val arch: Property<EclipseArch> = project.objects.property()
  val mirrorUrl: Property<String> = project.objects.property()
  val majorVersion: Property<String> = project.objects.property()
  val minorVersion: Property<String> = project.objects.property()

  init {
    os.convention(EclipseOs.current())
    arch.convention(EclipseArch.current())
    mirrorUrl.convention("https://mirror.dkm.cz/eclipse/")
    majorVersion.convention("2020-06")
    minorVersion.convention("R")
  }

  internal val groupId: Provider<String> = majorVersion.flatMap { majorVersion -> minorVersion.map { minorVersion -> "eclipse-$majorVersion-$minorVersion" } }
  internal val prefixUrl: Provider<String> = majorVersion.flatMap { majorVersion -> minorVersion.map { minorVersion -> "technology/epp/downloads/release/$majorVersion/$minorVersion/eclipse-committers-$majorVersion-$minorVersion" } }
  internal val url: Provider<String> = mirrorUrl.flatMap { mirrorUrl -> prefixUrl.flatMap { prefixUrl -> os.flatMap { os -> arch.map { arch -> "$mirrorUrl/$prefixUrl-${os.archiveSuffix}${arch.archiveSuffix}.${os.archiveExtension}" } } } }

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
    extension.os.get().pluginsDir,
    extension.os.get().configurationDir,
    extension.groupId.get(),
    GradleLog(project.logger)
  )
  project.extra.set(MavenizePlugin.lazilyMavenizedExtraName, mavenized)
  return mavenized
}
