package mb.coronium.task

import mb.coronium.plugin.internal.lazilyGetMavenizedEclipseInstallation
import mb.coronium.plugin.internal.mavenizeExtension
import mb.coronium.util.toPortableString
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.kotlin.dsl.*
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

open class EclipseRun : JavaExec() {
  private val eclipseRunConfigFile = project.buildDir.toPath().resolve("eclipseRun/config.ini")

  @Input
  val application: Property<String> = project.objects.property()

  @Input
  val product: Property<String> = project.objects.property()

  @Internal
  val bundles = ArrayList<Path>()

  init {
    application.convention("org.eclipse.ui.ide.workbench")
    product.convention("org.eclipse.platform.ide")

    workingDir = eclipseRunConfigFile.parent.toFile()
    main = "-Dosgi.configuration.cascaded=true"
  }


  fun addBundle(bundle: Path) {
    bundles.add(bundle)
  }

  fun addBundle(bundle: File) {
    bundles.add(bundle.toPath())
  }

  fun addBundle(archiveTask: AbstractArchiveTask) {
    bundles.add(archiveTask.archiveFile.get().asFile.toPath())
  }

  fun addBundles(fileCollection: FileCollection) {
    for(file in fileCollection) {
      bundles.add(file.toPath())
    }
  }


  fun prepareEclipseRunConfig() {
    application.finalizeValue()
    val application = application.get()
    product.finalizeValue()
    val product = product.get()

    val mavenizedExtension = project.mavenizeExtension()
    mavenizedExtension.finalizeOsArch()
    val mavenized = project.lazilyGetMavenizedEclipseInstallation()

    args(mavenizedExtension.os.get().extraJvmArgs)
    args(
      "-Dosgi.sharedConfiguration.area=.",
      "-Dosgi.sharedConfiguration.area.readOnly=true",
      "-Dosgi.configuration.area=configuration",
      "-jar", mavenized.equinoxLauncherPath(),
      "-clean", // Clean the OSGi cache so that rewiring occurs, which is needed when bundles change.
      "-data", "workspace",
      "-consoleLog"
    )

    val osgiFramework = mavenized.osgiFrameworkPath()!!
    val equinoxConfigurator = mavenized.equinoxConfiguratorPath()!!
    val equinoxConfiguratorBundlesInfo = mavenized.equinoxConfiguratorBundlesInfoPath()
    Files.createDirectories(eclipseRunConfigFile.parent)
    Files.newOutputStream(eclipseRunConfigFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE).buffered().use { outputStream ->
      PrintWriter(outputStream).use { writer ->
        writer.println("eclipse.application=$application")
        writer.println("eclipse.product=$product")
        writer.println("""osgi.framework=file\:${osgiFramework.toPortableString()}""")
        writer.print("""osgi.bundles=reference\:file\:${equinoxConfigurator.toPortableString()}@1\:start""")
        for(bundle in bundles) {
          writer.print(",${bundle.toPortableString()}")
        }
        writer.println()
        writer.println("osgi.bundles.defaultStartLevel=4")
        writer.println("""org.eclipse.equinox.simpleconfigurator.configUrl=file\:${equinoxConfiguratorBundlesInfo.toPortableString()}""")
        writer.flush()
      }
      outputStream.flush()
    }
  }
}
