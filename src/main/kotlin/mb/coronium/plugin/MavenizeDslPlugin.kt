package mb.coronium.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
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

open class MavenizeExtension {
  var os: EclipseOs = EclipseOs.current()
  var arch: EclipseArch = EclipseArch.current()
  var mirrorUrl: String = "http://ftp.fau.de"
  var prefixUrl: String = "eclipse/technology/epp/downloads/release/photon/R/eclipse-committers-photon-R"
  var groupId: String = "eclipse-photon"
  var mavenizeDir: Path = Paths.get(System.getProperty("user.home"), ".mavenize")

  val url get() = "$mirrorUrl/$prefixUrl-${os.archiveSuffix}${arch.archiveSuffix}.${os.archiveExtension}"
}

class MavenizeDslPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = MavenizeExtension()
    project.extensions.add("mavenize", extension)
  }
}

fun Project.mavenizeExtension() = project.extensions.getByType<MavenizeExtension>()
