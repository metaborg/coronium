package mb.coronium.task

import mb.coronium.plugin.internal.MavenizeExtension
import mb.coronium.mavenize.MavenizedEclipseInstallation
import org.gradle.api.tasks.JavaExec

open class EclipseRun : JavaExec() {
  fun configure(prepareEclipseRunConfig: PrepareEclipseRunConfig, installation: MavenizedEclipseInstallation, extension: MavenizeExtension) {
    dependsOn(prepareEclipseRunConfig)
    val configFile = prepareEclipseRunConfig.eclipseRunConfigFile
    inputs.file(configFile)
    workingDir = configFile.parent.toFile()
    main = "-Dosgi.configuration.cascaded=true"
    args(extension.os.extraJvmArgs)
    args(
      "-Dosgi.sharedConfiguration.area=.",
      "-Dosgi.sharedConfiguration.area.readOnly=true",
      "-Dosgi.configuration.area=configuration",
      "-jar", installation.equinoxLauncherPath(),
      "-clean", // Clean the OSGi cache so that rewiring occurs, which is needed when bundles change.
      "-data", "workspace",
      "-consoleLog"
    )
  }
}
