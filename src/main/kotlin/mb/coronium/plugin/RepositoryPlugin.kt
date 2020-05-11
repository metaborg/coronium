package mb.coronium.plugin

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.eclipse.*
import mb.coronium.model.maven.MavenVersion
import mb.coronium.plugin.internal.*
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*

@Suppress("unused")
open class RepositoryExtension(private val project: Project) {
  var repositoryDescriptionFile: String = "category.xml"
  var qualifierReplacement: String = SimpleDateFormat("yyyyMMddHHmm").format(Calendar.getInstance().time)
  var createPublication: Boolean = true


  private fun ModuleDependency.configureArtifact() {
    this.artifact {
      this.name = this.name
      this.type = "feature"
      this.extension = "jar"
    }
  }

  private fun createDependency(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    dependency.configureArtifact()
    return dependency
  }

  private fun createDependency(projectPath: String): Dependency {
    val dependency = project.dependencies.project(projectPath, Dependency.DEFAULT_CONFIGURATION)
    dependency.configureArtifact()
    return dependency
  }


  fun requireFeature(group: String, name: String, version: String): Dependency {
    val dependency = createDependency(group, name, version)
    requireFeature(dependency)
    return dependency
  }

  fun requireFeature(projectPath: String): Dependency {
    val dependency = createDependency(projectPath)
    requireFeature(dependency)
    return dependency
  }

  private fun requireFeature(dependency: Dependency) {
    project.bundleRuntimeClasspathConfig().dependencies.add(dependency) // TODO: fix wrong configuration
  }


//  private val featureConfig = project.featureConfig
//  internal val featureDependencyToCategoryName = mutableMapOf<Dependency, String>()
//
//  fun feature(group: String, name: String, version: String, categoryName: String? = null): Dependency {
//    val dependency = project.dependencies.create(group, name, version, FeatureBasePlugin.feature)
//    featureConfig.dependencies.add(dependency)
//    if(categoryName != null) {
//      featureDependencyToCategoryName[dependency] = categoryName
//    }
//    return dependency
//  }
//
//  fun featureProject(path: String, categoryName: String? = null): Dependency {
//    val dependency = project.dependencies.project(path, FeatureBasePlugin.feature)
//    featureConfig.dependencies.add(dependency)
//    if(categoryName != null) {
//      featureDependencyToCategoryName[dependency] = categoryName
//    }
//    return dependency
//  }
}

@Suppress("unused")
class RepositoryPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.extensions.add("repository", RepositoryExtension(project))
    project.gradle.projectsEvaluated { configure(project) }
  }

  private fun configure(project: Project) {
    val log = GradleLog(project.logger)
    val extension = project.extensions.getByType<RepositoryExtension>()

    project.pluginManager.apply(BasePlugin::class)

    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    val bundleRuntimeConfig = project.bundleRuntimeClasspathConfig() // TODO: fix wrong configuration

    // Build repository model from repository description file (category.xml or site.xml) and Gradle project.
    val repository = run {
      val builder = Repository.Builder()
      val repositoryDescriptionFile = project.file(extension.repositoryDescriptionFile).toPath()
      if(Files.isRegularFile(repositoryDescriptionFile)) {
        builder.readFromRepositoryXml(repositoryDescriptionFile, log)
      }
      // Add dependencies from Gradle project.
      for(dependency in bundleRuntimeConfig.allDependencies) {
        if(dependency.version == null) {
          error("Cannot convert dependency $dependency to a feature dependency, as it it has no version")
        }
        val version = MavenVersion.parse(dependency.version!!).toEclipse()
        //val categoryName = extension.featureDependencyToCategoryName[dependency]
        val categoryName = null // TODO: restore category
        builder.dependencies.add(Repository.Dependency(Repository.Dependency.Coordinates(dependency.name, version), categoryName))
      }
      builder.build()
    }

    // Update Gradle project model from feature model.
    run {
      // Create converter.
      val groupId = project.group.toString()
      val converter = mavenized.createConverter(groupId)
      // Add dependencies to bundle configuration when it is empty. Not using default dependencies due to https://github.com/gradle/gradle/issues/7943.
      if(bundleRuntimeConfig.allDependencies.isEmpty()) {
        for(featureDependency in repository.dependencies) {
          val coords = converter.convert(featureDependency.coordinates)
          val isMavenizedBundle = mavenized.isMavenizedBundle(coords.groupId, coords.id)
          // Skip dependencies to mavenized bundles, as they are provided and should not be included in features.
          if(!isMavenizedBundle) {
            val gradleDependency = coords.toGradleDependency(project, bundleRuntimeConfig.name)
            bundleRuntimeConfig.dependencies.add(gradleDependency)
          }
        }
      }
    }

    // Unpack dependency features.
    val unpackFeaturesDir = project.buildDir.resolve("unpackFeatures")
    unpackFeaturesDir.mkdirs()
    val unpackFeaturesTask = project.tasks.create("unpackFeatures") {
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      outputs.dir(unpackFeaturesDir)
      doFirst {
        unpackFeaturesDir.deleteRecursively()
        unpackFeaturesDir.mkdirs()
      }
      doLast {
        project.copy {
          bundleRuntimeConfig.forEach {
            from(project.zipTree(it))
          }
          into(unpackFeaturesDir)
        }
      }
    }

    // Replace '.qualifier' with concrete qualifiers in all features and plugins. Have to do the unpacking/packing of
    // JAR files manually, as we cannot create Gradle tasks during execution.
    val featuresInUnpackFeaturesDir = unpackFeaturesDir.resolve("features").toPath()
    val pluginsInUnpackFeaturesDir = unpackFeaturesDir.resolve("plugins").toPath()
    val concreteQualifier = extension.qualifierReplacement
    val replaceQualifierDir = project.buildDir.resolve("replaceQualifier")
    val featuresInReplaceQualifierDir = replaceQualifierDir.resolve("features").toPath()
    val pluginsInReplaceQualifierDir = replaceQualifierDir.resolve("plugins").toPath()
    val replaceQualifierTask = project.tasks.create("replaceQualifier") {
      dependsOn(unpackFeaturesTask)
      inputs.dir(unpackFeaturesDir)
      outputs.dir(replaceQualifierDir)
      doFirst {
        replaceQualifierDir.deleteRecursively()
        Files.createDirectories(featuresInUnpackFeaturesDir)
        Files.createDirectories(pluginsInUnpackFeaturesDir)
        Files.createDirectories(featuresInReplaceQualifierDir)
        Files.createDirectories(pluginsInReplaceQualifierDir)
      }
      doLast {
        TempDir("replaceQualifier").use { tempDir ->
          Files.list(featuresInUnpackFeaturesDir).use { featureJarFiles ->
            for(featureJarFile in featureJarFiles) {
              val fileName = featureJarFile.fileName
              val unpackTempDir = tempDir.createTempDir(fileName.toString())
              unpack(featureJarFile, unpackTempDir, log)
              val featureXmlFile = unpackTempDir.resolve("feature.xml")
              if(Files.isRegularFile(featureXmlFile)) {
                val builder = Feature.Builder()
                builder.readFromFeatureXml(featureXmlFile, log)
                // Replace qualifier in version.
                builder.version = builder.version?.mapQualifier {
                  it?.replace("qualifier", concreteQualifier)
                }
                // Replace qualifier in dependencies.
                builder.dependencies = builder.dependencies.map { dep ->
                  dep.mapVersion { version ->
                    version.mapQualifier {
                      it?.replace("qualifier", concreteQualifier)
                    }
                  }
                }.toMutableList()
                // Write back feature XML.
                val feature = builder.build()
                Files.newOutputStream(featureXmlFile).buffered().use { outputStream ->
                  feature.writeToFeatureXml(outputStream)
                  outputStream.flush()
                }
              } else {
                log.warning("Unable to replace qualifiers in versions for $fileName, as it has no feature.xml file")
              }
              val targetJarFile = featuresInReplaceQualifierDir.resolve(fileName)
              packJar(unpackTempDir, targetJarFile)
            }
          }
          Files.list(pluginsInUnpackFeaturesDir).use { pluginJarFiles ->
            for(pluginJarFile in pluginJarFiles) {
              val fileName = pluginJarFile.fileName
              val unpackTempDir = tempDir.createTempDir(fileName.toString())
              unpack(pluginJarFile, unpackTempDir, log)
              val manifestFile = unpackTempDir.resolve("META-INF/MANIFEST.MF")
              if(Files.isRegularFile(manifestFile)) {
                val manifest = readManifestFromFile(manifestFile)
                val builder = Bundle.Builder()
                builder.readFromManifestAttributes(manifest.mainAttributes, log)
                // Replace qualifier in version.
                builder.coordinates.version = builder.coordinates.version?.mapQualifier {
                  it?.replace("qualifier", concreteQualifier)
                }
                // Replace qualifier in dependencies.
                builder.requiredBundles = builder.requiredBundles.map { dep ->
                  dep.mapVersion { version ->
                    when(version) {
                      is BundleVersion -> version.mapQualifier { it?.replace("qualifier", concreteQualifier) }
                      is BundleVersionRange -> version.mapQualifiers { it?.replace("qualifier", concreteQualifier) }
                      null -> null
                    }
                  }
                }.toMutableList()
                // Write back to manifest attributes.
                val bundle = builder.build()
                val attributesMap = bundle.writeToManifestAttributes()
                for((name, value) in attributesMap) {
                  manifest.mainAttributes.putValue(name, value)
                }
                // Write manifest back to file.
                Files.newOutputStream(manifestFile).buffered().use { outputStream ->
                  manifest.write(outputStream)
                  outputStream.flush()
                }
              } else {
                log.warning("Unable to replace qualifiers in versions for $fileName, as it has no META-INF/MANIFEST.MF file")
              }
              val targetJarFile = pluginsInReplaceQualifierDir.resolve(fileName)
              packJar(unpackTempDir, targetJarFile)
            }
          }
        }
      }
    }

    // Build repository.xml from repository model.
    val repositoryXmlFile = project.buildDir.resolve("repositoryXml/repository.xml").toPath()
    val createRepositoryXmlTask = project.tasks.create("createRepositoryXml") {
      // Depend on (files from) feature configuration because dependencies in this configuration affect the repository model.
      dependsOn(bundleRuntimeConfig)
      inputs.files(bundleRuntimeConfig)
      outputs.file(repositoryXmlFile)
      doLast {
        Files.createDirectories(repositoryXmlFile.parent)
        Files.newOutputStream(repositoryXmlFile).buffered().use { outputStream ->
          repository.writeToRepositoryXml(outputStream)
          outputStream.flush()
        }
      }
    }

    // Build the repository.
    val repositoryDir = project.buildDir.resolve("repository")
    val eclipseLauncherPath = mavenized.equinoxLauncherPath()?.toString() ?: error("Could not find Eclipse launcher")
    val createRepositoryTask = project.tasks.create("createRepository") {
      dependsOn(replaceQualifierTask)
      dependsOn(createRepositoryXmlTask)
      inputs.dir(replaceQualifierDir)
      inputs.file(eclipseLauncherPath)
      inputs.file(repositoryXmlFile)
      outputs.dir(repositoryDir)
      doFirst {
        repositoryDir.deleteRecursively()
        repositoryDir.mkdirs()
      }
      doLast {
        project.javaexec {
          main = "-jar"
          args = mutableListOf(
            eclipseLauncherPath,
            "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
            "-metadataRepository", "file:/$repositoryDir",
            "-artifactRepository", "file:/$repositoryDir",
            "-source", "$replaceQualifierDir",
            "-configs", "ANY",
            "-compress",
            "-publishArtifacts"
          )
        }
        project.javaexec {
          main = "-jar"
          args = mutableListOf(
            eclipseLauncherPath,
            "-application", "org.eclipse.equinox.p2.publisher.CategoryPublisher",
            "-metadataRepository", "file:/$repositoryDir",
            "-categoryDefinition", "file:/$repositoryXmlFile",
            "-categoryQualifier",
            "-compress"
          )
        }
      }
    }

    // Zip the repository.
    val zipRepositoryTask = project.tasks.create<Zip>("zipRepository") {
      dependsOn(createRepositoryTask)
      from(repositoryDir)
    }
    project.tasks.getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(zipRepositoryTask)

    // Add the result of the ZIP task as an artifact.
    val artifact: PublishArtifact = project.artifacts.add(Dependency.DEFAULT_CONFIGURATION, zipRepositoryTask) {
      this.name = project.name
      this.extension = "zip"
      this.type = "repository"
      this.builtBy(zipRepositoryTask)
    }

    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("Repository") {
            artifact(artifact) {
              this.extension = "zip"
            }
            pom {
              packaging = "zip"
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

    // Run Eclipse with unpacked plugins.
    val prepareEclipseRunConfigurationTask = project.tasks.create<PrepareEclipseRunConfig>("prepareRunConfiguration") {
      dependsOn(unpackFeaturesTask)
      setFromMavenizedEclipseInstallation(mavenized)
      doFirst {
        Files.list(pluginsInUnpackFeaturesDir).use { pluginFiles ->
          for(file in pluginFiles) {
            addBundle(file)
          }
        }
      }
    }
    project.tasks.create<EclipseRun>("run") {
      configure(prepareEclipseRunConfigurationTask, mavenized, project.mavenizeExtension())
    }
  }
}
