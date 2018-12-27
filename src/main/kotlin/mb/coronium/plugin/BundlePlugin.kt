package mb.coronium.plugin

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.eclipse.*
import mb.coronium.model.maven.MavenVersionOrRange
import mb.coronium.plugin.internal.*
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import java.nio.file.Files

@Suppress("unused")
open class BundleExtension(private val project: Project) {
  private val bundleImplementationConfig = project.bundleImplementationConfig
  private val bundleImplementationProvidedConfig = project.bundleImplementationProvidedConfig

  fun bundleImplementation(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, BundleBasePlugin.bundle)
    bundleImplementationConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleImplementationProvided(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, BundleBasePlugin.bundle)
    bundleImplementationProvidedConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleImplementationProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, BundleBasePlugin.bundle)
    bundleImplementationConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleImplementationProvidedProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, BundleBasePlugin.bundle)
    bundleImplementationProvidedConfig.dependencies.add(dependency)
    return dependency
  }

  @JvmOverloads
  fun bundleImplementationTargetPlatform(name: String, version: String? = null): Dependency {
    val groupId = project.mavenizeExtension().groupId
    // Dependency to target platform bundle uses 'default' target configuration, as that is the configuration which the Maven artifacts for
    // the target platform bundles are in.
    val dependency = project.dependencies.create(groupId, name, version ?: "[0,)", Dependency.DEFAULT_CONFIGURATION)
    bundleImplementationProvidedConfig.dependencies.add(dependency)
    return dependency
  }


  private val bundleApiConfig = project.bundleApiConfig
  private val bundleApiProvidedConfig = project.bundleApiProvidedConfig

  fun bundleApi(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, BundleBasePlugin.bundle)
    bundleApiConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleApiProvided(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, BundleBasePlugin.bundle)
    bundleApiProvidedConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleApiProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, BundleBasePlugin.bundle)
    bundleApiConfig.dependencies.add(dependency)
    return dependency
  }

  fun bundleApiProvidedProject(path: String): Dependency {
    val dependency = project.dependencies.project(path, BundleBasePlugin.bundle)
    bundleApiProvidedConfig.dependencies.add(dependency)
    return dependency
  }

  @JvmOverloads
  fun bundleApiTargetPlatform(name: String, version: String? = null): Dependency {
    val groupId = project.mavenizeExtension().groupId
    // Dependency to target platform bundle uses 'default' target configuration, as that is the configuration which the Maven artifacts for
    // the target platform bundles are in.
    val dependency = project.dependencies.create(groupId, name, version ?: "[0,)", Dependency.DEFAULT_CONFIGURATION)
    bundleApiProvidedConfig.dependencies.add(dependency)
    return dependency
  }
}

@Suppress("unused")
class BundlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(BundleBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.extensions.add("eclipsePlugin", BundleExtension(project))
    project.afterEvaluate { configure(this) }
  }

  private fun configure(project: Project) {
    val log = GradleLog(project.logger)
    val bundleApiConfig = project.bundleApiConfig
    val bundleApiProvidedConfig = project.bundleApiProvidedConfig
    val bundleImplementationConfig = project.bundleImplementationConfig
    val bundleImplementationProvidedConfig = project.bundleImplementationProvidedConfig
    val bundleConfig = project.bundleConfig

    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    // Make the Java plugin's configurations extend our plugin configuration, so that all dependencies from our configurations are included
    // in the Java plugin configurations.
    project.pluginManager.apply(JavaLibraryPlugin::class)
    project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME).extendsFrom(bundleApiProvidedConfig)
    project.configurations.getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(bundleImplementationProvidedConfig)

    // Read META-INF/MANIFEST.MF into manifest, if any.
    val manifestFile = project.file("META-INF/MANIFEST.MF").toPath()
    val manifest = run {
      if(manifestFile != null) {
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
        // TODO: set project name from manifest in a Settings plugin, instead of just checking it?
        log.warning("Project name '${project.name}' differs from manifest name '${builder.coordinates.name}'; using '${project.name}' instead")
        builder.coordinates.name = project.name
      }
      // Set version from Gradle project if no version was set.
      if(builder.coordinates.version == null) {
        if(project.version == Project.DEFAULT_VERSION) {
          error("Cannot configure Eclipse plugin project; no project version was set, nor has a version been set in $manifestFile")
        }
        builder.coordinates.version = project.eclipseVersion
      }
      // Add dependencies from Gradle project.
      for(dependency in bundleApiConfig.dependencies + bundleApiProvidedConfig.dependencies) {
        if(dependency.version == null) {
          error("Cannot convert Gradle bundle api dependency '$dependency' to a Require-Bundle dependency, as it it has no version")
        }
        val version = MavenVersionOrRange.parse(dependency.version!!).toEclipse()
        builder.requiredBundles.add(BundleDependency(dependency.name, version, DependencyResolution.Mandatory, DependencyVisibility.Reexport))
      }
      for(dependency in bundleImplementationConfig.dependencies + bundleImplementationProvidedConfig.dependencies) {
        if(dependency.version == null) {
          error("Cannot convert Gradle bundle implementation dependency '$dependency' to a Require-Bundle dependency, as it it has no version")
        }
        val version = MavenVersionOrRange.parse(dependency.version!!).toEclipse()
        builder.requiredBundles.add(BundleDependency(dependency.name, version, DependencyResolution.Mandatory, DependencyVisibility.Private))
      }
      builder.build()
    }

    // Update Gradle project model from bundle model.
    run {
      // Convert bundle into a Maven (Gradle-compatible) artifact.
      val groupId = project.group.toString()
      val converter = mavenized.createConverter(groupId)
      converter.recordBundle(bundle, groupId)
      val mavenArtifact = converter.convert(bundle)
      // Set project version only if it it has not been set yet.
      if(project.version == Project.DEFAULT_VERSION) {
        project.version = mavenArtifact.coordinates.version
      }
      // Add dependencies to bundle dependency configurations when they are empty. Not using default dependencies due to https://github.com/gradle/gradle/issues/7943.
      if(bundleConfig.allDependencies.isEmpty()) {
        for(mavenDependency in mavenArtifact.dependencies) {
          val coords = mavenDependency.coordinates
          val isMavenizedBundle = mavenized.isMavenizedBundle(coords.groupId, coords.id)
          val sourceConfiguration = when(mavenDependency.scope) {
            null -> bundleApiConfig
            "compile" -> if(isMavenizedBundle) bundleApiProvidedConfig else bundleApiConfig
            "provided" -> if(isMavenizedBundle) bundleImplementationProvidedConfig else bundleImplementationConfig
            else -> bundleApiConfig
          }
          // Dependency to target platform bundle uses 'default' target configuration, as that is the configuration which the Maven
          // artifacts for the target platform bundles are in.
          val targetConfiguration = if(isMavenizedBundle) Dependency.DEFAULT_CONFIGURATION else bundleConfig.name
          val gradleDependency = coords.toGradleDependency(project, targetConfiguration)
          sourceConfiguration.dependencies.add(gradleDependency)
        }
      }
    }

    // Build a final manifest from the existing manifest and the bundle model, and set it in the JAR task.
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
    val prepareManifestTask = project.tasks.create("prepareManifestTask") {
      // Depend on manifest file, because it influences how the manifest is created, which in turn influences the final manifest.
      inputs.files(manifestFile)
      // Depend on bundle configuration, because it influence how a bundle model is built, which in turn influences the final manifest.
      dependsOn(bundleConfig)
      doLast {
        if(manifest != null) {
          jarTask.manifest.attributes(manifest.mainAttributes.toStringMap())
        }
        jarTask.manifest.attributes(bundle.writeToManifestAttributes())
      }
    }
    jarTask.dependsOn(prepareManifestTask)

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
          outputDir = project.file(properties.outputDir!!)
        }
      }
    }
    // Add resources from build properties.
    project.tasks.getByName<ProcessResources>(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
      from(project.projectDir) {
        for(resource in properties.binaryIncludes) {
          include(resource)
        }
      }
    }
    // Publish the result of the JAR task in the 'bundle' configuration.
    project.artifacts {
      add(bundleConfig.name, jarTask)
    }

    // Run Eclipse with this plugin and its dependencies.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      dependsOn(jarTask)
      dependsOn(bundleConfig)
      setFromMavenizedEclipseInstallation(mavenized)
      doFirst {
        addBundle(jarTask)
        for(file in bundleConfig) {
          addBundle(file)
        }
      }
    }
    project.tasks.create<EclipseRun>("run") {
      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
