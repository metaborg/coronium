package mb.coronium.task

import mb.coronium.plugin.internal.lazilyMavenize
import mb.coronium.plugin.internal.mavenizeExtension
import mb.coronium.util.Arch
import mb.coronium.util.Os
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*
import java.nio.file.Path

@Suppress("UnstableApiUsage")
open class EclipseCreateInstallation : JavaExec() {
  @get:Input
  val repositories: ListProperty<String> = project.objects.listProperty()

  @get:Input
  val installUnits: ListProperty<String> = project.objects.listProperty()

  @get:Input
  val buildDirName: Property<String> = project.objects.property()

  @get:OutputDirectory
  val destination: Property<Path> = project.objects.property()

  @get:Input
  val appName: Property<String> = project.objects.property()

  @get:Input
  val os: Property<Os> = project.objects.property()

  @get:Input
  val arch: Property<Arch> = project.objects.property()


  @get:Input
  @get:Optional
  val iniRequiredJavaVersion: Property<String> = project.objects.property()

  @get:Input
  @get:Optional
  val iniStackSize: Property<String> = project.objects.property()

  @get:Input
  @get:Optional
  val iniHeapSize: Property<String> = project.objects.property()

  @get:Input
  @get:Optional
  val iniMaxHeapSize: Property<String> = project.objects.property()


  @get:Internal
  val appDirName: Provider<String> = os.flatMap { os ->
    appName.map { appName ->
      if(os == Os.OSX) {
        "$appName.app"
      } else {
        appName
      }
    }
  }

  @get:Internal
  val finalDestination: Provider<Path> = destination.flatMap { destination ->
    appDirName.map { appDirName ->
      destination.resolve(appDirName)
    }
  }

  @get:Internal
  val iniFile: Provider<Path> = finalDestination.flatMap { finalDestination -> os.map { os -> finalDestination.resolve(os.installationIniRelativePath) } }


  init {
    main = "-jar"

    repositories.value(listOf(
      "https://artifacts.metaborg.org/content/groups/eclipse-2021-03/"
    ))
    installUnits.value(listOf(
      "org.eclipse.platform.ide",
      "org.eclipse.platform.feature.group",
      "org.eclipse.epp.package.common.feature.feature.group",
      "org.eclipse.equinox.p2.user.ui.feature.group",
      "org.eclipse.epp.mpc.feature.group",
      "epp.package.java",
      "org.eclipse.jdt.feature.group",
      "org.eclipse.wst.xml_ui.feature.feature.group",
      "org.eclipse.egit.feature.group",
      "org.eclipse.jgit.feature.group",
      "org.eclipse.buildship.feature.group"
    ))
    buildDirName.convention(os.flatMap { os -> arch.map { arch -> "eclipse-${os.p2OsName}-${arch.p2ArchName}" } })
    destination.convention(buildDirName.map { buildDirectoryName -> project.buildDir.toPath().resolve(buildDirectoryName) })
    appName.convention("Eclipse")
    os.convention(Os.current())
    arch.convention(Arch.current())

    iniRequiredJavaVersion.convention("11")
    iniStackSize.convention("16M")
    iniMaxHeapSize.convention("4G")
  }

  @TaskAction
  override fun exec() {
    val mavenizedExtension = project.mavenizeExtension()
    mavenizedExtension.finalizeOsArch()
    val mavenized = project.lazilyMavenize()

    args(
      mavenized.equinoxLauncherPath(),
      "-application", "org.eclipse.equinox.p2.director"
    )
    repositories.finalizeValue()
    repositories.get().forEach { args("-repository", it) }
    installUnits.finalizeValue()
    installUnits.get().forEach { args("-installIU", it) }
    args("-tag", "InitialState")
    destination.finalizeValue()
    destination.get().toFile().deleteRecursively() // Delete and recreate destination to ensure a clean installation.
    destination.get().toFile().mkdirs()
    appName.finalizeValue()
    args("-destination", finalDestination.get())
    args("-profile", "SDKProfile")
    args("-profileProperties", " org.eclipse.update.install.features=true")
    os.finalizeValue()
    args("-p2.os", os.get().p2OsName)
    args("-p2.ws", os.get().p2WsName)
    arch.finalizeValue()
    args("-p2.arch", arch.get().p2ArchName)
    args("-roaming")

    super.exec()

    // Improve INI file.
    val iniFile = iniFile.get().toFile()
    var iniFileText = iniFile.readText()
    // Remove small fonts, as they are too small.
    iniFileText = iniFileText.replace(Regex("""-Dorg\.eclipse\.swt\.internal\.carbon\.smallFonts"""), "")
    // Remove multiple `--add-modules=ALL-SYSTEM` arguments. Will be added back once.
    iniFileText = iniFileText.replace(Regex("""--add-modules=ALL-SYSTEM"""), "")
    // Remove multiple `-XstartOnFirstThread` arguments. Will be added back only for macOS.
    iniFileText = iniFileText.replace(Regex("""-XstartOnFirstThread"""), "")
    // Remove multiple `-Dosgi.requiredJavaVersion=11` arguments. Will be added back with better defaults.
    iniFileText = iniFileText.replace(Regex("""-Dosgi.requiredJavaVersion=[0-9.]+"""), "")
    // Remove `-Xms -Xss -Xmx` arguments. Will be added back with better defaults.
    iniFileText = iniFileText.replace(Regex("""-X(ms|ss|mx)[0-9]+[gGmMkK]"""), "")

    iniFileText += "--add-modules=ALL-SYSTEM\n"
    if(os.get() == Os.OSX) {
      iniFileText += "-XstartOnFirstThread\n"
    }
    iniRequiredJavaVersion.finalizeValue()
    if(iniRequiredJavaVersion.isPresent) {
      iniFileText += "-Dosgi.requiredJavaVersion=${iniRequiredJavaVersion.get()}\n"
    }
    iniStackSize.finalizeValue()
    if(iniStackSize.isPresent) {
      iniFileText += "-Xss${iniStackSize.get()}\n"
    }
    iniHeapSize.finalizeValue()
    if(iniHeapSize.isPresent) {
      iniFileText += "-Xms${iniHeapSize.get()}\n"
    }
    iniMaxHeapSize.finalizeValue()
    if(iniMaxHeapSize.isPresent) {
      iniFileText += "-Xmx${iniMaxHeapSize.get()}\n"
    }

    iniFileText = iniFileText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") + "\n"

    iniFile.writeText(iniFileText)
  }
}
