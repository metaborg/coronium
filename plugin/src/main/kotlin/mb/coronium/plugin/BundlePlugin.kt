package mb.coronium.plugin

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.eclipse.BuildProperties
import mb.coronium.model.eclipse.Bundle
import mb.coronium.model.eclipse.BundleDependency
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.DependencyResolution
import mb.coronium.model.eclipse.DependencyVisibility
import mb.coronium.model.maven.MavenVersionOrRange
import mb.coronium.plugin.base.BundleBasePlugin
import mb.coronium.plugin.base.bundleElements
import mb.coronium.plugin.base.bundleRuntimeClasspath
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.lazilyMavenize
import mb.coronium.plugin.internal.mavenizeExtension
import mb.coronium.task.EclipseRun
import mb.coronium.util.GradleLog
import mb.coronium.util.eclipseVersion
import mb.coronium.util.readManifestFromFile
import mb.coronium.util.toStringMap
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
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

  fun createEclipseTargetPlatformDependency(name: String, version: String? = null): Provider<ExternalModuleDependency> {
    return project.mavenizeExtension().groupId.map {
      project.dependencies.create(it, name, version ?: "[0,)")
    }
  }


  init {
    manifestFile.convention(project.file("META-INF/MANIFEST.MF"))
  }

  internal val log: GradleLog = GradleLog(project.logger)
}

@Suppress("unused")
class BundlePlugin : Plugin<Project> {
  companion object {
    const val bundleApi = "bundleApi"
    const val bundleImplementation = "bundleImplementation"
    const val bundleEmbedApi = "bundleEmbedApi"
    const val bundleEmbedImplementation = "bundleEmbedImplementation"
    const val bundleTargetPlatformApi = "bundleTargetPlatformApi"
    const val bundleTargetPlatformImplementation = "bundleTargetPlatformImplementation"

    const val bundleEmbedClasspath = "bundleEmbedClasspath"
    const val requireBundleReexport = "requireBundleReexport"
    const val requireBundlePrivate = "requireBundlePrivate"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(JavaLibraryPlugin::class)
    project.pluginManager.apply(MavenizePlugin::class)
    project.pluginManager.apply(BundleBasePlugin::class)

    val extension = BundleExtension(project)
    project.extensions.add("bundle", extension)

    configure(project, extension)
  }

  private fun configure(project: Project, extension: BundleExtension) {
    configureConfigurations(project)
    configureBndTask(project)
    configureBuildProperties(project)
    configureJarTask(project, extension)
    configureRunEclipseTask(project)
  }

  private fun configureConfigurations(project: Project) {
    // User-facing configurations
    val bundleApi = project.configurations.create(bundleApi) {
      description = "API dependencies to bundles with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleImplementation = project.configurations.create(bundleImplementation) {
      description = "Implementation dependencies to bundles with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleEmbedApi = project.configurations.create(bundleEmbedApi) {
      description = "API dependencies to Java libraries embedded into the bundle, with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleEmbedImplementation = project.configurations.create(bundleEmbedImplementation) {
      description = "Implementation dependencies to Java libraries embedded into the bundle, with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
      extendsFrom(bundleEmbedApi)
    }
    val bundleTargetPlatformApi = project.configurations.create(bundleTargetPlatformApi) {
      description = "API dependencies to target platform bundles with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleTargetPlatformImplementation = project.configurations.create(bundleTargetPlatformImplementation) {
      description = "Implementation dependencies to target platform bundles with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }

    // Internal (resolvable) configurations
    val bundleEmbedClasspathConfiguration = project.configurations.register(bundleEmbedClasspath) {
      description = "Classpath for JARs to embed into the bundle"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      extendsFrom(bundleEmbedImplementation)
    }
    bundleEmbedClasspathConfiguration.configure { withDependencies { project.lazilyMavenize() } }
    val requireBundleReexportConfiguration = project.configurations.register(requireBundleReexport) {
      description = "Require-Bundle dependencies with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(bundleApi, bundleTargetPlatformApi)
    }
    requireBundleReexportConfiguration.configure { withDependencies { project.lazilyMavenize() } }
    val requireBundlePrivateConfiguration = project.configurations.register(requireBundlePrivate) {
      description = "Require-Bundle dependencies with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(bundleImplementation, bundleTargetPlatformImplementation)
    }
    requireBundlePrivateConfiguration.configure { withDependencies { project.lazilyMavenize() } }

    // Extend bundleElements, such that the dependencies to bundles are exported when deployed, which
    // can then be consumed by other projects.
    project.bundleElements.extendsFrom(bundleApi, bundleImplementation)

    // Extend bundleRuntimeClasspath, such that dependent-to bundles are loaded when running Eclipse.
    project.bundleRuntimeClasspath.extendsFrom(bundleApi, bundleImplementation)

    // Register the output of the Jar task as an artifact for the bundleRuntimeElements configuration.
    project.bundleElements.outgoing.artifact(project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME))

    // Connection to Java configurations.
    project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(
      bundleApi,
      bundleImplementation,
      bundleEmbedImplementation,
      bundleTargetPlatformApi,
      bundleTargetPlatformImplementation
    )
    project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME).extendsFrom(
      bundleApi,
      bundleEmbedApi,
      bundleTargetPlatformApi
    )

    // Add variants from bundleElements to the Java component.
    @Suppress("UnstableApiUsage") run {
      val javaComponent = project.components.findByName("java") as AdhocComponentWithVariants
      javaComponent.addVariantsFromConfiguration(project.bundleElements) {
        mapToMavenScope("runtime")
      }
    }
  }

  private fun configureBndTask(project: Project) {
    project.pluginManager.apply(aQute.bnd.gradle.BndBuilderPlugin::class)
    project.tasks.named<Jar>("jar").configure {
      withConvention(aQute.bnd.gradle.BundleTaskConvention::class) {
        setClasspath({ project.bundleEmbedClasspath }) // Closure to defer configuration resolution until task execution.
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

  private fun configureBuildProperties(project: Project) {
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
      named(SourceSet.MAIN_SOURCE_SET_NAME) {
        java {
          if(!properties.sourceDirs.isEmpty()) {
            setSrcDirs(properties.sourceDirs)
          }
          if(properties.outputDir != null) {
            @Suppress("UnstableApiUsage")
            outputDir = project.file(properties.outputDir)
          }
        }
      }
    }

    // Add resources from build properties.
    @Suppress("UnstableApiUsage")
    project.tasks.named<ProcessResources>(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
      from(project.projectDir) {
        for(resource in properties.binaryIncludes) {
          include(resource)
        }
      }
    }
  }

  private fun configureJarTask(project: Project, extension: BundleExtension) {
    val manifestFileProperty = extension.manifestFile
    val log = extension.log

    // Build a final manifest from the manifest's main attributes and bundle model, and set it in the JAR task.
    project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME).configure {
      // Depend on bundle compile configurations, because they influence how a bundle model is built, which in turn influences the final manifest.
      dependsOn(project.requireBundleReexport)
      inputs.files({ project.requireBundleReexport }) // Closure to defer configuration resolution until task execution.
      dependsOn(project.requireBundlePrivate)
      inputs.files({ project.requireBundlePrivate }) // Closure to defer configuration resolution until task execution.
      // Depend on manifest file, because it influences how the manifest is created, which in turn influences the final manifest.
      inputs.files(manifestFileProperty)

      doFirst {
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

        val jarManifest = this@configure.manifest
        if(manifest != null) {
          jarManifest.attributes(manifest.mainAttributes.toStringMap())
        }
        jarManifest.attributes(bundle.writeToManifestAttributes())
      }
    }
  }

  private fun configureRunEclipseTask(project: Project) {
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
    project.tasks.register<EclipseRun>("runEclipse") {
      group = "coronium"
      description = "Runs this plugin in an Eclipse instance"

      val bundleRuntimeClasspath = project.bundleRuntimeClasspath

      // Depend on the built bundle JAR.
      dependsOn(jarTask)
      // Depend on the bundle runtime configurations.
      dependsOn(bundleRuntimeClasspath)
      inputs.files({ bundleRuntimeClasspath }) // Closure to defer configuration resolution until task execution.

      doFirst {
        // At task execution time, before the run configuration is prepared, add the JAR as a bundle, and add all
        // runtime dependencies as bundles. This is done at task execution time because it resolves the configurations.
        addBundle(jarTask)
        for(file in bundleRuntimeClasspath) {
          addBundle(file)
        }
        // Prepare run configuration before executing.
        prepareEclipseRunConfig()
      }
    }
  }
}

private val Project.bundleEmbedClasspath get(): Configuration = this.configurations.getByName(BundlePlugin.bundleEmbedClasspath)
private val Project.requireBundleReexport get(): Configuration = this.configurations.getByName(BundlePlugin.requireBundleReexport)
private val Project.requireBundlePrivate get(): Configuration = this.configurations.getByName(BundlePlugin.requireBundlePrivate)
