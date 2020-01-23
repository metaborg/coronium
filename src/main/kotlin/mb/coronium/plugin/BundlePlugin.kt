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
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.File
import java.nio.file.Files

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class BundleExtension(private val project: Project) {
  var manifestFile: File = project.file("META-INF/MANIFEST.MF")
  var createPublication: Boolean = false


  private fun createDependency(group: String, name: String, version: String, configuration: String? = null) =
    project.dependencies.create(group, name, version, configuration)

  private fun createDependency(projectPath: String, configuration: String? = null) =
    project.dependencies.project(projectPath, configuration)


  fun requireTargetPlatform(name: String, version: String? = null, reexport: Boolean = true): Dependency {
    val group = project.mavenizeExtension().groupId
    val dependency = createDependency(group, name, version ?: "[0,)", Dependency.DEFAULT_CONFIGURATION)
    project.bundleCompileConfig(reexport).dependencies.add(dependency)
    return dependency
  }


  fun requireBundle(group: String, name: String, version: String, reexport: Boolean = true): Dependency {
    val dependency = createDependency(group, name, version)
    requireBundle(dependency, reexport)
    return dependency
  }

  fun requireBundle(projectPath: String, reexport: Boolean = true): Dependency {
    val dependency = createDependency(projectPath)
    requireBundle(dependency, reexport)
    return dependency
  }

  fun requireBundle(dependencyNotation: Any, reexport: Boolean = true) {
    val dependency = project.dependencies.create(dependencyNotation)
    val compileDependency = dependency.copy()
    if(compileDependency is ModuleDependency) {
      compileDependency.targetConfiguration = ConfigNames.bundleCompileReexport
    }
    project.bundleCompileConfig(reexport).dependencies.add(compileDependency)
    if(dependency is ModuleDependency) {
      dependency.targetConfiguration = ConfigNames.bundleRuntime
    }
    project.bundleRuntimeConfig().dependencies.add(dependency)
  }


  fun requireEmbeddingBundle(group: String, name: String, version: String, reexport: Boolean = true): Dependency {
    val dependency = createDependency(group, name, version)
    requireEmbeddingBundle(dependency, reexport)
    return dependency
  }

  fun requireEmbeddingBundle(projectPath: String, reexport: Boolean = true): Dependency {
    val dependency = createDependency(projectPath)
    requireEmbeddingBundle(dependency, reexport)
    return dependency
  }

  fun requireEmbeddingBundle(dependencyNotation: Any, reexport: Boolean = true) {
    val dependency = project.dependencies.create(dependencyNotation)
    // Add transitive dependency to a Java configuration, as this dependency contains Java libraries which should not leak into the bundle configurations.
    val javaConfig = project.configurations.getByName(if(reexport) JavaPlugin.API_CONFIGURATION_NAME else JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
    javaConfig.dependencies.add(dependency)
    // Add non-transitive dependency to bundle configurations, to prevent Java dependencies from leaking into the bundle configurations.
    val bundleDependency = dependency.copy()
    if(bundleDependency is ModuleDependency) {
      bundleDependency.isTransitive = false
    }
    project.bundleCompileConfig(reexport).dependencies.add(bundleDependency)
    project.bundleRuntimeConfig().dependencies.add(bundleDependency)
  }
}

@Suppress("unused")
class BundlePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.pluginManager.apply(JavaBasePlugin::class)
    project.extensions.add("bundle", BundleExtension(project))
    project.afterEvaluate { configure(this) }
  }

  private fun configure(project: Project) {
    val log = GradleLog(project.logger)
    val extension = project.extensions.getByType<BundleExtension>()

    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    run {
      // Apply Java library plugin to get access to its configurations.
      project.pluginManager.apply(JavaLibraryPlugin::class)

      // Extend their configurations with Coronium's.
      val compileOnlyConfig = project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
      compileOnlyConfig.extendsFrom(project.bundleCompileConfig(false))
      compileOnlyConfig.extendsFrom(project.bundleCompileConfig(true))

      val runtimeOnlyConfig = project.configurations.getByName(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME)
      runtimeOnlyConfig.extendsFrom(project.bundleRuntimeConfig())
    }

    // Read manifest file if it exists.
    val manifestFile = extension.manifestFile.toPath()
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
        // TODO: set project name from manifest in a Settings plugin, instead of just checking it?
        log.warning("Project name '${project.name}' differs from manifest name '${builder.coordinates.name}'; using '${project.name}' instead")
        builder.coordinates.name = project.name
      }
      // Set version from Gradle project if no version was set.
      if(builder.coordinates.version == null) {
        builder.coordinates.version = if(project.version == Project.DEFAULT_VERSION) {
          log.warning("No project version was set, nor has a version been set in $manifestFile, defaulting to version 0")
          BundleVersion.zero()
        } else {
          project.eclipseVersion
        }
      }
      // Add bundle compile dependencies from Gradle project.
      for(dependency in project.bundleCompileConfig(reexport = true).dependencies) {
        if(dependency.version == null) {
          log.warning("Cannot convert Gradle bundle (reexported) dependency '$dependency' to a Require-Bundle dependency, as it it has no version. This dependency will not be included in the bundle manifest, probably making the bundle unusable in OSGi/Eclipse")
          continue;
        }
        val version = MavenVersionOrRange.parse(dependency.version!!).toEclipse()
        builder.requiredBundles.add(BundleDependency(dependency.name, version, DependencyResolution.Mandatory, DependencyVisibility.Reexport))
      }
      for(dependency in project.bundleCompileConfig(reexport = false).dependencies) {
        if(dependency.version == null) {
          log.warning("Cannot convert Gradle bundle dependency '$dependency' to a Require-Bundle dependency, as it it has no version. This dependency will not be included in the bundle manifest, probably making the bundle unusable in OSGi/Eclipse")
          continue;
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
      // Add dependencies from model when none are declared. Not using default dependencies due to https://github.com/gradle/gradle/issues/7943.
      if(project.bundleCompileConfig(reexport = true).allDependencies.isEmpty()) {
        for(mavenDependency in mavenArtifact.dependencies) {
          val coords = mavenDependency.coordinates
          val isMavenizedBundle = mavenized.isMavenizedBundle(coords.groupId, coords.id)
          val reexport = when(mavenDependency.scope) {
            "provided" -> false
            else -> true
          }
          val gradleDependency = coords.toGradleDependency(project, Dependency.DEFAULT_CONFIGURATION)
          project.bundleCompileConfig(reexport).dependencies.add(gradleDependency)
          if(!isMavenizedBundle) {
            // Only add runtime dependency when the dependency is not a target platform (Mavenized bundle) dependency.
            project.bundleRuntimeConfig().dependencies.add(gradleDependency)
          }
        }
      }
    }

    // Build a final manifest from the existing manifest and the bundle model, and set it in the JAR task.
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
    val prepareManifestTask = project.tasks.create("prepareManifestTask") {
      // Depend on bundle compile configurations, because they influences how a bundle model is built, which in turn influences the final manifest.
      dependsOn(project.bundleCompileConfig(reexport = false))
      inputs.files(project.bundleCompileConfig(reexport = false))
      dependsOn(project.bundleCompileConfig(reexport = true))
      inputs.files(project.bundleCompileConfig(reexport = true))
      // Depend on manifest file, because it influences how the manifest is created, which in turn influences the final manifest.
      inputs.files(manifestFile)
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
          outputDir = project.file(properties.outputDir)
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

    // Export bundle configurations
    project.artifacts.add(ConfigNames.bundleCompileReexport, jarTask)
    project.artifacts.add(ConfigNames.bundleRuntime, jarTask)

    // TODO: these configurations are exported, but not yet published, so they will not be available when resolving to
    //   a published bundle. Publish them via a software component? See:
    // * https://docs.gradle.org/5.3/javadoc/org/gradle/api/component/SoftwareComponentFactory.html
    // * https://github.com/gradle/gradle/issues/2355#issuecomment-314917242
    // * {@link JavaPlugin#registerSoftwareComponents}

    if(extension.createPublication) {
      // Add Java component as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        val component = project.components.getByName("java")
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("Bundle") {
            from(component)
          }
        }
      }
    }

    // Run Eclipse with this plugin and its dependencies.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      val bundleRuntimeConfig = project.bundleRuntimeConfig()
      // Depend on the built bundle JAR.
      dependsOn(jarTask)
      // Depend on the bundle runtime configurations.
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      // Set run configuration from Mavenized Eclipse installation.
      setFromMavenizedEclipseInstallation(mavenized)
      doFirst {
        // At task execution time, before the run configuration is prepared, add the JAR as a bundle, and add all
        // runtime dependencies as bundles. This is done at task execution time because it resolves the configurations.
        addBundle(jarTask)
        for(file in bundleRuntimeConfig) {
          addBundle(file)
        }
      }
    }
    project.tasks.create<EclipseRun>("run") {
      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
