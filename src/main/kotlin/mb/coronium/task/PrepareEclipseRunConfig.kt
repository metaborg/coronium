package mb.coronium.task

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.util.toPortableString
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

open class PrepareEclipseRunConfig : DefaultTask() {
  @get:Input
  var application = "org.eclipse.ui.ide.workbench"
  @get:Input
  var product = "org.eclipse.platform.ide"

  @get:InputFile
  lateinit var osgiFramework: Path
  @get:InputFile
  lateinit var equinoxConfigurator: Path
  @get:InputFile
  lateinit var equinoxConfiguratorBundlesInfo: Path

  fun setFromMavenizedEclipseInstallation(installation: MavenizedEclipseInstallation) {
    osgiFramework = installation.osgiFrameworkPath()
      ?: error("Could not set OSGi framework for PrepareEclipseRunConfig task from mavenized Eclipse installation, since it was not found")
    equinoxConfigurator = installation.equinoxConfiguratorPath()
      ?: error("Could not set Equinox configurator for PrepareEclipseRunConfig task from mavenized Eclipse installation, since it was not found")
    equinoxConfiguratorBundlesInfo = installation.equinoxConfiguratorBundlesInfoPath()
  }


  @get:Input
  val bundles = mutableListOf<Path>()

  fun addBundle(bundle: Path) {
    bundles.add(bundle)
  }

  fun addBundle(bundle: File) {
    bundles.add(bundle.toPath())
  }

  fun addBundle(archiveTask: AbstractArchiveTask) {
    bundles.add(archiveTask.archivePath.toPath())
  }

  fun addBundles(fileCollection: FileCollection) {
    for(file in fileCollection) {
      bundles.add(file.toPath())
    }
  }


  @get:OutputFile
  var eclipseRunConfigFile = project.buildDir.toPath().resolve("eclipseRun/config.ini")!!


  @TaskAction
  fun prepareEclipseRunConfig() {
    Files.newOutputStream(eclipseRunConfigFile).buffered().use { outputStream ->
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