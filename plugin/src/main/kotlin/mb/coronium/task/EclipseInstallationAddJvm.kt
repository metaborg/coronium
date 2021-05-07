package mb.coronium.task

import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.util.Arch
import mb.coronium.util.GradleLog
import mb.coronium.util.Os
import mb.coronium.util.downloadAndUnarchive
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import java.nio.file.Path

@Suppress("UnstableApiUsage")
open class EclipseInstallationAddJvm : DefaultTask() {
  @get:Internal
  val createTask: Property<TaskProvider<EclipseCreateInstallation>> = project.objects.property()


  @get:Input
  val jvmVersion: Property<String> = project.objects.property()

  @get:Input
  val jvmDownloadUrl: Property<String> = project.objects.property()


  @get:InputDirectory
  val copySrcDir: Provider<Path> = createTask.flatMap { it.flatMap { it.destination } }

  @get:OutputDirectory
  val copyDestDir: Provider<Path> = createTask.flatMap { it.flatMap { it.buildDirName.map { project.buildDir.resolve("$it-jvm").toPath() } } }

  @get:Internal
  val copyDestAppDir: Provider<Path> = copyDestDir.flatMap { destDir -> createTask.flatMap { it.flatMap { it.appDirName.map { destDir.resolve(it) } } } }


  @get:Input
  val os: Provider<Os> = createTask.flatMap { it.flatMap { it.os } }

  @get:Input
  val arch: Provider<Arch> = createTask.flatMap { it.flatMap { it.arch } }


  @get:Input
  val jvmDownloadVersion: Provider<String> = jvmVersion.map { it.replace('+', '-') }

  @get:Input
  val dirNameInJvmArchive: Provider<String> = jvmVersion.map { "jdk-$it" }

  @get:Input
  val jvmDestRelativePath: Provider<String> = project.provider { "jvm" }

  @get:OutputDirectory
  val jvmDestDir: Provider<Path> = copyDestAppDir.flatMap { destAppDir -> jvmDestRelativePath.map { destAppDir.resolve(it) } }


  @get:OutputFile
  val iniFile: Provider<Path> = copyDestAppDir.flatMap { destAppDir -> os.map { destAppDir.resolve(it.installationIniRelativePath) } }


  init {
    dependsOn(createTask)

    jvmVersion.convention("11.0.11+9")
    jvmDownloadUrl.convention(os.flatMap { os ->
      arch.flatMap { arch ->
        jvmDownloadVersion.map { version ->
          "https://artifacts.metaborg.org/content/repositories/releases/net/adoptopenjdk/jdk/$version/jdk-$version-${os.jvmDownloadUrlArchiveSuffix}-${arch.jreDownloadUrlArchiveSuffix}.${os.jvmDownloadUrlArchiveExtension}"
        }
      }
    })
  }

  @TaskAction
  fun exec() {
    // Sync Eclipse installation into separate directory
    createTask.finalizeValue()
    project.sync {
      from(copySrcDir)
      into(copyDestDir)
    }

    // Download and unarchive JRE
    val jvmCacheDir = MavenizePlugin.mavenizeDir.resolve("jvm_cache")
    jvmDownloadUrl.finalizeValue()
    val (jvmDir, _) = downloadAndUnarchive(jvmDownloadUrl.get(), jvmCacheDir, false, GradleLog(project.logger))

    // Sync JRE into Eclipse installation
    project.sync {
      from(dirNameInJvmArchive.map { jvmDir.resolve(it) })
      into(jvmDestDir)
    }

    // Modify Eclipse ini VM argument
    val iniFile = iniFile.get().toFile()
    var iniFileText = iniFile.readText()
    iniFileText = iniFileText.replace(Regex("""-vm\n.+\n""", RegexOption.MULTILINE), "")
    iniFileText = "-vm\n${os.get().installationJvmRelativePath(arch.get())}\n$iniFileText"
    iniFile.writeText(iniFileText)
  }
}
