package mb.coronium.plugin

import mb.coronium.mavenize.MavenizedEclipseInstallation
import mb.coronium.mavenize.toEclipse
import mb.coronium.model.eclipse.BuildProperties
import mb.coronium.model.eclipse.Bundle
import mb.coronium.model.eclipse.BundleDependency
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.DependencyResolution
import mb.coronium.model.eclipse.DependencyVisibility
import mb.coronium.model.maven.MavenVersionOrRange
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.mavenizedEclipseInstallation
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.GradleLog
import mb.coronium.util.eclipseVersion
import mb.coronium.util.readManifestFromFile
import mb.coronium.util.toStringMap
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.nio.file.Files

@Suppress("UnstableApiUsage")
open class BundleExtension(private val project: Project) {
  var manifestFile: Property<File> = project.objects.property()

  fun createEclipseTargetPlatformDependency(name: String, version: String? = null): Dependency {
    return project.dependencies.create(project.mavenizeExtension().groupId, name, version ?: "[0,)")
  }


  init {
    manifestFile.convention(project.file("META-INF/MANIFEST.MF"))
  }

  internal val log: GradleLog = GradleLog(project.logger)
}

@Suppress("unused")
class BundlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(BundleBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.pluginManager.apply(JavaLibraryPlugin::class)

    val extension = BundleExtension(project)
    project.extensions.add("bundle", extension)

    configure(project, extension)
  }

  private fun configure(project: Project, extension: BundleExtension) {
    // HACK: eagerly load Mavenize Plugin, as it adds a repository that must be available in the configuration phase.
    // This currently disables the ability for users to customize the Eclipse target platform.
    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    configureConfigurations(project)
    configureBuildPropertiesFile(project)
    configureFinalizeManifestTask(project, extension)
    configureBndTask(project)
    configureRunTask(project, mavenized)
  }

  private fun configureConfigurations(project: Project) {
    // Register the output of the Jar task as an artifact for the bundleRuntimeClasspath.
    project.bundleRuntimeClasspath.outgoing.artifact(project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME))

    // Connection to Java configurations.
    project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(
      project.bundleApi,
      project.bundleImplementation,
      project.bundleEmbedImplementation,
      project.bundleTargetPlatformApi,
      project.bundleTargetPlatformImplementation
    )
    project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME).extendsFrom(
      project.bundleApi,
      project.bundleEmbedApi,
      project.bundleTargetPlatformApi
    )

    // Add our variants to the Java component.
    @Suppress("UnstableApiUsage") run {
      val javaComponent = project.components.findByName("java") as AdhocComponentWithVariants
      javaComponent.addVariantsFromConfiguration(project.bundleRuntimeElements) {
        mapToMavenScope("runtime")
      }
    }
  }

  private fun configureBndTask(project: Project) {
    project.pluginManager.apply(aQute.bnd.gradle.BndBuilderPlugin::class)
    project.tasks.named<Jar>("jar").configure {
      withConvention(aQute.bnd.gradle.BundleTaskConvention::class) {
        setClasspath(project.bundleEmbedClasspath)
      }
      manifest {
        attributes(
          Pair("Import-Package", ""), // Disable imports
          Pair("-nouses", "true"), // Disable 'uses' directive generation for exports.
          Pair("-nodefaultversion", "true"), // Disable 'version' directive generation for exports.
          Pair("-fixupmessages", "Classpath is empty, cannot be found on the classpath") // Ignore some warnings caused by current setup.
        )
      }
    }
  }

  private fun configureBuildPropertiesFile(project: Project) {
    // Process build properties.
    val properties = run {
      val builder = BuildProperties.Builder()
      val propertiesFile = project.file("build.properties").toPath()
      if(Files.isRegularFile(propertiesFile)) {
        builder.readFromPropertiesFile(propertiesFile)
      } else {
        builder.binaryIncludes.add("plugin.xml")
      }
      // Remove '.' since it makes no sense to include everything.
      // Remove entries starting with 'META-INF', since META-INF/MANIFEST.MF is merged and included automatically.
      builder.binaryIncludes.removeIf { it == "." || it.startsWith("META-INF") }
      builder.build()
    }

    // Set source directories and output directory from build properties.
    project.configure<SourceSetContainer> {
      val mainSourceSet = getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      mainSourceSet.java {
        if(!properties.sourceDirs.isEmpty()) {
          setSrcDirs(properties.sourceDirs)
        }
        if(properties.outputDir != null) {
          @Suppress("UnstableApiUsage")
          outputDir = project.file(properties.outputDir)
        }
      }
    }

    // Add resources from build properties.
    @Suppress("UnstableApiUsage")
    project.tasks.getByName<ProcessResources>(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
      from(project.projectDir) {
        for(resource in properties.binaryIncludes) {
          include(resource)
        }
      }
    }
  }

  private fun configureFinalizeManifestTask(project: Project, extension: BundleExtension) {
    val manifestFileProperty = extension.manifestFile
    val log = extension.log

    // Build a final manifest from the manifest's main attributes and bundle model, and set it in the JAR task.
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
    val finalizeBundleManifestTask = project.tasks.create("finalizeBundleManifest") {
      group = "coronium"
      description = "Finalizes the manifest from the bundle manifest, and dependencies provided to Gradle into a single manifest in the resulting JAR file"

      // Depend on bundle compile configurations, because they influence how a bundle model is built, which in turn influences the final manifest.
      dependsOn(project.requireBundleReexport)
      inputs.files({ project.requireBundleReexport }) // Closure to defer configuration resolution until task execution.
      dependsOn(project.requireBundlePrivate)
      inputs.files({ project.requireBundlePrivate }) // Closure to defer configuration resolution until task execution.
      // Depend on manifest file, because it influences how the manifest is created, which in turn influences the final manifest.
      inputs.files(manifestFileProperty)

      doLast {
        manifestFileProperty.finalizeValue()
        val manifestFile = manifestFileProperty.get().toPath()

        // Read manifest file if it exists.
        val manifest = run {
          if(Files.exists(manifestFile)) {
            readManifestFromFile(manifestFile)
          } else {
            null
          }
        }

        // Build bundle model from manifest and Gradle project.
        val bundle = run {
          val builder = Bundle.Builder()

          // Read bundle configuration from manifest if it exists.
          if(manifest != null) {
            builder.readFromManifestAttributes(manifest.mainAttributes, log)
          }

          // Set name fom Gradle project if no name was set, or if the name does not match.
          if(builder.coordinates.name == null) {
            builder.coordinates.name = project.name
          } else if(builder.coordinates.name != project.name) {
            log.warning("Project name '${project.name}' differs from bundle coordinates name '${builder.coordinates.name}' in '$manifestFile'; using '${project.name}' instead")
            builder.coordinates.name = project.name
          }

          // Set version from Gradle project if no version was set, or if the version does not match.
          if(builder.coordinates.version == null) {
            builder.coordinates.version = if(project.version == Project.DEFAULT_VERSION) {
              log.warning("Project '${project.name}' has no version set (i.e., version is '${Project.DEFAULT_VERSION}'), nor has a version been set in '$manifestFile', defaulting to version '${BundleVersion.zero()}'")
              BundleVersion.zero()
            } else {
              project.eclipseVersion
            }
          } else if(builder.coordinates.version != project.eclipseVersion) {
            log.warning("Eclipsified version '${project.eclipseVersion}' of project '${project.name}' differs from bundle coordinates version '${builder.coordinates.version}' in '$manifestFile'; using '${project.eclipseVersion}' instead")
            builder.coordinates.version = project.eclipseVersion
          }

          // Add required bundles from Gradle project.
          for(artifact in project.requireBundleReexport.resolvedConfiguration.resolvedArtifacts) {
            val module = artifact.moduleVersion.id
            val version = MavenVersionOrRange.parse(module.version).toEclipse()
            builder.requiredBundles.add(BundleDependency(module.name, version, DependencyResolution.Mandatory, DependencyVisibility.Reexport))
          }
          for(artifact in project.requireBundlePrivate.resolvedConfiguration.resolvedArtifacts) {
            val module = artifact.moduleVersion.id
            val version = MavenVersionOrRange.parse(module.version).toEclipse()
            builder.requiredBundles.add(BundleDependency(module.name, version, DependencyResolution.Mandatory, DependencyVisibility.Private))
          }

          builder.build()
        }

        if(manifest != null) {
          jarTask.manifest.attributes(manifest.mainAttributes.toStringMap())
        }
        jarTask.manifest.attributes(bundle.writeToManifestAttributes())
      }
    }
    jarTask.dependsOn(finalizeBundleManifestTask)
  }

  private fun configureRunTask(project: Project, mavenized: MavenizedEclipseInstallation) {
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)

    // Run Eclipse with this plugin and its dependencies.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      group = "coronium"
      description = "Prepares an Eclipse run configuration for running this plugin in an Eclipse instance"

      val runtimeClasspathConfig = project.bundleRuntimeClasspath

      // Depend on the built bundle JAR.
      dependsOn(jarTask)
      // Depend on the bundle runtime configurations.
      dependsOn(runtimeClasspathConfig)
      inputs.files({ runtimeClasspathConfig }) // Closure to defer configuration resolution until task execution.

      // Set run configuration from Mavenized Eclipse installation.
      setFromMavenizedEclipseInstallation(mavenized)

      doFirst {
        // At task execution time, before the run configuration is prepared, add the JAR as a bundle, and add all
        // runtime dependencies as bundles. This is done at task execution time because it resolves the configurations.
        addBundle(jarTask)
        for(file in runtimeClasspathConfig) {
          addBundle(file)
        }
      }
    }

    project.tasks.register<EclipseRun>("run") {
      group = "coronium"
      description = "Runs this plugin in an Eclipse instance"

      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
