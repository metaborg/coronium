package mb.coronium.plugin

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.model.eclipse.BuildProperties
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.Feature
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.mavenizedEclipseInstallation
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.GradleLog
import mb.coronium.util.eclipseVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

@Suppress("unused")
open class FeatureExtension(private val project: Project) {
  val createPublication: Property<Boolean> = project.objects.property()

  init {
    createPublication.convention(true)
  }


  internal val log = GradleLog(project.logger)
}

@Suppress("unused")
class FeaturePlugin @Inject constructor(
  private val softwareComponentFactory: SoftwareComponentFactory
) : Plugin<Project> {
  companion object {
    const val featureXmlFilename = "feature.xml"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)

    val extension = FeatureExtension(project)
    project.extensions.add("feature", extension)

    configure(project, extension)
  }

  private fun configure(project: Project, extension: FeatureExtension) {
    // HACK: eagerly load Mavenize Plugin, as it adds a repository that must be available in the configuration phase.
    // This currently disables the ability for users to customize the Eclipse target platform.
    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    val featureXmlOutputDir = project.buildDir.resolve("feature")
    val featureXmlOutputFile = featureXmlOutputDir.resolve(featureXmlFilename)

    val buildProperties = configureBuildProperties(project)
    val finalizeFeatureXmlTask = configureFinalizeFeatureXmlTask(project, extension, featureXmlOutputFile)
    configureFeatureJarTask(project, extension, buildProperties, featureXmlOutputDir, finalizeFeatureXmlTask)
    configureRunTask(project, mavenized)
  }

  private fun configureBuildProperties(project: Project): BuildProperties {
    val builder = BuildProperties.Builder()
    val propertiesFile = project.file("build.properties").toPath()
    if(Files.isRegularFile(propertiesFile)) {
      builder.readFromPropertiesFile(propertiesFile)
    }
    // Remove 'feature.xml', since it is merged and included automatically.
    builder.binaryIncludes.removeIf {
      it == featureXmlFilename
    }
    return builder.build()
  }

  private fun configureFinalizeFeatureXmlTask(
    project: Project,
    extension: FeatureExtension,
    featureXmlOutputFile: File
  ): TaskProvider<*> {
    return project.tasks.register("finalizeFeatureXml") {
      // Depend on bundle runtime configurations, because they influence how a feature model is built, which in turn influences the final feature XML file.
      dependsOn(project.bundleRuntimeClasspath)
      inputs.files({ project.bundleRuntimeClasspath }) // Closure to defer configuration resolution until task execution.

      outputs.file(featureXmlOutputFile)

      doLast {
        // Build feature model from feature.xml and Gradle project.
        val feature = run {
          val builder = Feature.Builder()
          // Read feature model from feature.xml if it exists.
          val featureXmlFile = project.file(featureXmlFilename).toPath()
          if(Files.isRegularFile(featureXmlFile)) {
            builder.readFromFeatureXml(featureXmlFile, extension.log)
          }

          // Set name fom Gradle project if no name was set, or if the name does not match.
          if(builder.id == null) {
            builder.id = project.name
          } else if(builder.id != project.name) {
            extension.log.warning("Project name '${project.name}' differs from feature id '${builder.id}'; using '${project.name}' instead")
            builder.id = project.name
          }

          // Set version from Gradle project if no version was set, or if the version does not match.
          if(builder.version == null) {
            builder.version = if(project.version == Project.DEFAULT_VERSION) {
              extension.log.warning("Project '${project.name}' has no version set (i.e., version is '${Project.DEFAULT_VERSION}'), nor has a version been set in '$featureXmlFile', defaulting to version '${BundleVersion.zero()}'")
              BundleVersion.zero()
            } else {
              project.eclipseVersion
            }
          } else if(builder.version != project.eclipseVersion) {
            extension.log.warning("Eclipsified version '${project.eclipseVersion}' of project '${project.name}' differs from feature version '${builder.version}' in '$featureXmlFile'; using '${project.eclipseVersion}' instead")
            builder.version = project.eclipseVersion
          }

          builder.build()
        }

        // Merge (add or replace version of) bundle dependencies from bundleRuntimeClasspath into the feature.
        val mergedFeature = feature.mergeWith(project.bundleRuntimeClasspath.resolvedConfiguration)

        // Write the feature to a feature.xml file.
        featureXmlOutputFile.outputStream().buffered().use { outputStream ->
          mergedFeature.writeToFeatureXml(outputStream)
          outputStream.flush()
        }
      }
    }
  }

  private fun configureFeatureJarTask(
    project: Project,
    extension: FeatureExtension,
    buildProperties: BuildProperties,
    featureXmlOutputDir: File,
    finalizeFeatureXmlTask: TaskProvider<*>
  ) {
    val featureJarTask = project.tasks.register<Jar>("featureJar") {
      dependsOn(finalizeFeatureXmlTask)

      if(!buildProperties.binaryIncludes.isEmpty()) {
        from(project.projectDir) {
          for(resource in buildProperties.binaryIncludes) {
            include(resource)
          }
        }
      }

      from(featureXmlOutputDir) {
        include(featureXmlFilename)
      }
    }

    // Register the output of the Jar task as an artifact for the bundleRuntimeClasspath.
    project.featureRuntimeClasspath.outgoing.artifact(featureJarTask)

    // Create a new feature component and add our variants to it.
    val featureComponent = @Suppress("UnstableApiUsage") run {
      val featureComponent = softwareComponentFactory.adhoc("feature")
      project.components.add(featureComponent)
      featureComponent.addVariantsFromConfiguration(project.featureRuntimeElements) {
        mapToMavenScope("runtime")
      }
      featureComponent
    }

    // Create a publication from the feature component.
    if(extension.createPublication.get()) {
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("Feature") {
            from(featureComponent)
          }
        }
      }
    }
  }

  private fun configureRunTask(project: Project, mavenized: MavenizedEclipseInstallation) {
    // Run Eclipse with direct dependencies.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      group = "coronium"
      description = "Prepares an Eclipse run configuration for running this feature in an Eclipse instance"

      val runtimeClasspathConfig = project.bundleRuntimeClasspath

      // Depend on the bundle runtime configurations.
      dependsOn(runtimeClasspathConfig)
      inputs.files({ runtimeClasspathConfig }) // Closure to defer configuration resolution until task execution.

      setFromMavenizedEclipseInstallation(mavenized)

      doFirst {
        // At task execution time, before the run configuration is prepared, add all runtime dependencies as bundles.
        // This is done at task execution time because it resolves the configurations.
        for(file in runtimeClasspathConfig) {
          addBundle(file)
        }
      }
    }

    project.tasks.register<EclipseRun>("run") {
      group = "coronium"
      description = "Runs this feature in an Eclipse instance"

      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
