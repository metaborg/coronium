package mb.coronium.plugin

import mb.coronium.mavenize.toMaven
import mb.coronium.model.eclipse.BuildProperties
import mb.coronium.model.eclipse.Feature
import mb.coronium.plugin.internal.*
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import java.nio.file.Files

@Suppress("unused")
open class FeatureExtension(private val project: Project) {
  var createPublication: Boolean = false


  private fun createDependency(group: String, name: String, version: String) =
    project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)

  private fun createDependency(projectPath: String) =
    project.dependencies.project(projectPath, Dependency.DEFAULT_CONFIGURATION)


  fun requireBundle(group: String, name: String, version: String): Dependency {
    val dependency = createDependency(group, name, version)
    requireBundle(dependency)
    return dependency
  }

  fun requireBundle(projectPath: String): Dependency {
    val dependency = createDependency(projectPath)
    requireBundle(dependency)
    return dependency
  }

  private fun requireBundle(dependency: Dependency) {
    project.bundleRuntimeConfig().dependencies.add(dependency)
  }
}

@Suppress("unused")
class FeaturePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.extensions.add("feature", FeatureExtension(project))
    project.afterEvaluate { configure(this) }
  }

  private fun configure(project: Project) {
    val log = GradleLog(project.logger)
    val extension = project.extensions.getByType<FeatureExtension>()

    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    val bundleRuntimeConfig = project.bundleRuntimeConfig()

    // Build feature model from feature.xml and Gradle project.
    val feature = run {
      val builder = Feature.Builder()
      // Read feature model from feature.xml if it exists.
      val featureXmlFile = project.file("feature.xml").toPath()
      if(Files.isRegularFile(featureXmlFile)) {
        builder.readFromFeatureXml(featureXmlFile, log)
      }
      // Set name fom Gradle project if no name was set, or if the name does not match.
      if(builder.id == null) {
        builder.id = project.name
      } else if(builder.id != project.name) {
        // TODO: set project name from manifest in a Settings plugin, instead of just checking it?
        log.warning("Project name '${project.name}' differs from feature id '${builder.id}'; using '${project.name}' instead")
        builder.id = project.name
      }
      // Set version from Gradle project if no version was set.
      if(builder.version == null) {
        if(project.version == Project.DEFAULT_VERSION) {
          error("Cannot configure Eclipse feature project; no project version was set, nor has a version been set in $featureXmlFile")
        }
        builder.version = project.eclipseVersion
      }
      builder.build()
    }

    // Update Gradle project model from feature model.
    run {
      // Create converter.
      val groupId = project.group.toString()
      val converter = mavenized.createConverter(groupId)
      // Set project version only if it it has not been set yet.
      if(project.version == Project.DEFAULT_VERSION) {
        project.version = feature.version.toMaven()
      }
      // Add dependencies from model when none are declared. Not using default dependencies due to https://github.com/gradle/gradle/issues/7943.
      if(bundleRuntimeConfig.allDependencies.isEmpty()) {
        for(featureDependency in feature.dependencies) {
          val coords = converter.convert(featureDependency.coordinates)
          val isMavenizedBundle = mavenized.isMavenizedBundle(coords.groupId, coords.id)
          // Skip dependencies to mavenized bundles, as they are provided and should not be included in features.
          if(!isMavenizedBundle) {
            val gradleDependency = coords.toGradleDependency(project)
            project.bundleRuntimeConfig().dependencies.add(gradleDependency)
          }
        }
      }
    }

    // Process build properties.
    val properties = run {
      val builder = BuildProperties.Builder()
      val propertiesFile = project.file("build.properties").toPath()
      if(Files.isRegularFile(propertiesFile)) {
        builder.readFromPropertiesFile(propertiesFile)
      }
      // Remove 'feature.xml', since it is merged and included automatically.
      builder.binaryIncludes.removeIf {
        it == "feature.xml"
      }
      builder.build()
    }

    // Build feature.xml from the feature model.
    val featureXmlDir = project.buildDir.resolve("featureXml")
    val featureXmlTask = project.tasks.create("featureXmlTask") {
      // Depend on runtime configuration because dependencies in this configuration affect the feature model.
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      // Output feature XML file
      val featureXmlFile = featureXmlDir.resolve("feature.xml").toPath()
      outputs.file(featureXmlFile)
      doLast {
        val mergedFeature = feature.mergeWith(project.bundleRuntimeConfig())
        Files.newOutputStream(featureXmlFile).buffered().use { outputStream ->
          mergedFeature.writeToFeatureXml(outputStream)
          outputStream.flush()
        }
      }
    }
    // Create feature JAR with built feature.xml and other included resources.
    val targetDir = project.buildDir.resolve("feature")
    val targetFeaturesDir = targetDir.resolve("features")
    val featureJarTask = project.tasks.create<Jar>("featureJar") {
      dependsOn(featureXmlTask)
      destinationDirectory.set(targetFeaturesDir)
      if(!properties.binaryIncludes.isEmpty()) {
        from(project.projectDir) {
          for(resource in properties.binaryIncludes) {
            include(resource)
          }
        }
      }
      from(featureXmlDir) {
        include("feature.xml")
      }
      doFirst {
        targetFeaturesDir.deleteRecursively()
        targetFeaturesDir.mkdirs()
      }
    }
    // Copy dependency bundles into 'plugins' directory.
    val targetPluginsDir = targetDir.resolve("plugins")
    val copyBundlesTask = project.tasks.create("copyBundles") {
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      outputs.dir(targetPluginsDir)
      doFirst {
        targetPluginsDir.deleteRecursively()
        targetPluginsDir.mkdirs()
      }
      doLast {
        project.copy {
          from(bundleRuntimeConfig.resolvedConfiguration.firstLevelModuleDependencies.flatMap { it.moduleArtifacts }.map { it.file })
          into(targetPluginsDir)
        }
      }
    }
    // Create a final JAR that includes the feature and all dependency bundles.
    val jarTask = project.tasks.create<Jar>("jar") {
      dependsOn(featureJarTask, copyBundlesTask)
      from(targetDir)
    }
    project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(jarTask)


    // Add the result of the JAR task as an artifact.
    val artifact = project.artifacts.add(Dependency.DEFAULT_CONFIGURATION, jarTask) {
      this.name = project.name
      this.extension = "jar"
      this.type = "feature"
      this.builtBy(jarTask)
    }
    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("Feature") {
            artifact(artifact) {
              this.extension = "jar"
            }
            pom {
              packaging = "jar"
              withXml {
                val root = asElement()
                val doc = root.ownerDocument
                val dependenciesNode = doc.createElement("dependencies")
                for(dependency in bundleRuntimeConfig.dependencies) {
                  val dependencyNode = doc.createElement("dependency")

                  val groupIdNode = doc.createElement("groupId")
                  groupIdNode.appendChild(doc.createTextNode(dependency.group))
                  dependencyNode.appendChild(groupIdNode)

                  val artifactIdNode = doc.createElement("artifactId")
                  artifactIdNode.appendChild(doc.createTextNode(dependency.name))
                  dependencyNode.appendChild(artifactIdNode)

                  val versionNode = doc.createElement("version")
                  versionNode.appendChild(doc.createTextNode(dependency.version))
                  dependencyNode.appendChild(versionNode)

                  val scopeNode = doc.createElement("scope")
                  scopeNode.appendChild(doc.createTextNode("provided"))
                  dependencyNode.appendChild(scopeNode)

                  dependenciesNode.appendChild(dependencyNode)
                }
                root.appendChild(dependenciesNode)
              }
            }
          }
        }
      }
    }

    // Run Eclipse with direct dependencies.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      setFromMavenizedEclipseInstallation(mavenized)
      doFirst {
        for(file in bundleRuntimeConfig.resolvedConfiguration.firstLevelModuleDependencies.flatMap { it.moduleArtifacts }.map { it.file }) {
          addBundle(file)
        }
      }
    }
    project.tasks.create<EclipseRun>("run") {
      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
