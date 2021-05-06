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

open class EclipseInstallationAddJre : DefaultTask() {
  @get:Internal
  val createTask: Property<TaskProvider<EclipseCreateInstallation>> = project.objects.property()

  @get:InputDirectory
  val sourceDir: Provider<Path> = createTask.flatMap { it.flatMap { it.destination } }

  @get:OutputDirectory
  val destDir: Provider<Path> = createTask.flatMap { it.flatMap { it.buildDirectoryName.map { project.buildDir.resolve("$it-jre").toPath() } } }

  @get:Internal
  val destAppDir: Provider<Path> = destDir.flatMap { destDir -> createTask.flatMap { it.flatMap { it.applicationDirectoryName.map { destDir.resolve(it) } } } }


  @get:Input
  val os: Property<Os> = project.objects.property()

  @get:Input
  val arch: Property<Arch> = project.objects.property()


  @get:Input
  val jreVersion: Property<String> = project.objects.property()

  @get:Input
  val jreDownloadVersion: Provider<String> = jreVersion.map { it.replace('+', '-') }

  @get:Input
  val jreDownloadUrl: Property<String> = project.objects.property()

  @get:Input
  val dirNameInJre: Provider<String> = jreVersion.map { "jdk-$it-jre" }

  @get:Input
  val jreDestinationRelativePath: Provider<String> = project.provider { "jre" }

  @get:OutputDirectory
  val jreDestinationDir: Provider<Path> = destAppDir.flatMap { destAppDir -> jreDestinationRelativePath.map { destAppDir.resolve(it) } }


  @get:OutputFile
  val iniFile: Provider<Path> = destAppDir.flatMap { destAppDir -> os.map { os -> destAppDir.resolve(os.installationIniRelativePath) } }


  init {
    os.convention(Os.current())
    arch.convention(Arch.current())
    jreVersion.convention("11.0.11+9")
    jreDownloadUrl.convention(os.flatMap { os ->
      arch.flatMap { arch ->
        jreDownloadVersion.map { jreDownloadVersion ->
          "https://artifacts.metaborg.org/content/repositories/releases/net/adoptopenjdk/jre/$jreDownloadVersion/jre-$jreDownloadVersion-${os.jreDownloadUrlArchiveSuffix}-${arch.jreDownloadUrlArchiveSuffix}.${os.jreDownloadUrlArchiveExtension}"
        }
      }
    })

    dependsOn(createTask)
  }

  @TaskAction
  fun exec() {
    // Sync Eclipse installation into separate directory
    createTask.finalizeValue()
    project.sync {
      from(sourceDir)
      into(destDir)
    }

    // Download and unarchive JRE
    val jreCacheDir = MavenizePlugin.mavenizeDir.resolve("jre_cache")
    os.finalizeValue()
    arch.finalizeValue()
    jreDownloadUrl.finalizeValue()
    val (jreDir, _) = downloadAndUnarchive(jreDownloadUrl.get(), jreCacheDir, false, GradleLog(project.logger))

    // Sync JRE into Eclipse installation
    project.sync {
      from(dirNameInJre.map { jreDir.resolve(it) })
      into(jreDestinationDir)
    }

    // Modify Eclipse ini VM argument
    val iniFile = iniFile.get().toFile()
    var iniFileText = iniFile.readText()
    iniFileText = iniFileText.replace(Regex("""-vm\n.+\n""", RegexOption.MULTILINE), "")
    iniFileText = "-vm\n${os.get().installationJreRelativePath(arch.get())}\n$iniFileText"
    iniFile.writeText(iniFileText)
  }
}
