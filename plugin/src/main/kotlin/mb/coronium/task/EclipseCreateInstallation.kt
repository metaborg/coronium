package mb.coronium.task

import mb.coronium.plugin.internal.EclipseArch
import mb.coronium.plugin.internal.EclipseOs
import mb.coronium.plugin.internal.lazilyMavenize
import mb.coronium.plugin.internal.mavenizeExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.*
import java.nio.file.Path

open class EclipseCreateInstallation : JavaExec() {
  @get:Input
  val repositories: ListProperty<String> = project.objects.listProperty()

  @get:Input
  val installUnits: ListProperty<String> = project.objects.listProperty()

  @get:OutputDirectory
  val destination: Property<Path> = project.objects.property()

  @get:Input
  val applicationName: Property<String> = project.objects.property()

  @get:Input
  val os: Property<EclipseOs> = project.objects.property()

  @get:Input
  val arch: Property<EclipseArch> = project.objects.property()


  @get:Internal
  val applicationDirectoryName: Provider<String> = os.map { os ->
    if(os == EclipseOs.OSX) {
      "${applicationName.get()}.app"
    } else {
      applicationName.get()
    }
  }

  @get:Internal
  val finalDestination: Provider<Path> = destination.flatMap { destination ->
    applicationDirectoryName.map { applicationDirectoryName ->
      destination.resolve(applicationDirectoryName)
    }
  }

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
    destination.convention(project.buildDir.toPath().resolve("eclipseInstallation"))
    applicationName.convention("Eclipse")
    os.convention(EclipseOs.current())
    arch.convention(EclipseArch.current())
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
    applicationName.finalizeValue()
    args("-destination", finalDestination.get())
    finalDestination.get().toFile().deleteRecursively()
    args("-profile", "SDKProfile")
    args("-profileProperties", " org.eclipse.update.install.features=true")
    os.finalizeValue()
    args("-p2.os", os.get().p2OsName)
    args("-p2.ws", os.get().p2WsName)
    arch.finalizeValue()
    args("-p2.arch", arch.get().p2ArchName)
    args("-roaming")

    super.exec()
  }
}
