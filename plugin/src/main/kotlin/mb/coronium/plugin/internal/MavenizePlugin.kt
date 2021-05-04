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
import java.io.Serializable
import java.nio.file.Path
import java.nio.file.Paths

enum class EclipseOs : Serializable {
  Windows {
    override val archiveSuffix = "win32"
    override val archiveExtension = "zip"
    override val pluginsDir = Paths.get("eclipse", "plugins")
    override val configurationDir = Paths.get("eclipse", "configuration")
    override val extraJvmArgs: List<String> = listOf()

    override val p2OsName = "win32"
    override val p2WsName = "win32"
  },
  Linux {
    override val archiveSuffix = "linux-gtk"
    override val archiveExtension = "tar.gz"
    override val pluginsDir = Paths.get("eclipse", "plugins")
    override val configurationDir = Paths.get("eclipse", "configuration")
    override val extraJvmArgs: List<String> = listOf()

    override val p2OsName = "linux"
    override val p2WsName = "gtk"
  },
  OSX {
    override val archiveSuffix = "macosx-cocoa"
    override val archiveExtension = "dmg"
    override val pluginsDir = Paths.get("Eclipse.app", "Contents", "Eclipse", "plugins")
    override val configurationDir = Paths.get("Eclipse.app", "Contents", "Eclipse", "configuration")
    override val extraJvmArgs: List<String> = listOf("-XstartOnFirstThread")

    override val p2OsName = "macosx"
    override val p2WsName = "cocoa"
  };

  abstract val archiveSuffix: String
  abstract val archiveExtension: String
  abstract val pluginsDir: Path
  abstract val configurationDir: Path
  abstract val extraJvmArgs: List<String>

  abstract val p2OsName: String
  abstract val p2WsName: String

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

enum class EclipseArch : Serializable {
  X86_32 {
    override val archiveSuffix = ""

    override val p2ArchName = "x86"
  },
  X86_64 {
    override val archiveSuffix = "-x86_64"

    override val p2ArchName = "x86_64"
  };

  abstract val archiveSuffix: String

  abstract val p2ArchName: String

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
