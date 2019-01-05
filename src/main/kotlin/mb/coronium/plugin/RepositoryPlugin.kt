package mb.coronium.plugin

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.eclipse.Repository
import mb.coronium.model.maven.MavenVersion
import mb.coronium.plugin.internal.FeatureBasePlugin
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.featureConfig
import mb.coronium.plugin.internal.mavenizedEclipseInstallation
import mb.coronium.task.EclipseRun
import mb.coronium.task.PrepareEclipseRunConfig
import mb.coronium.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.set

@Suppress("unused")
open class RepositoryExtension(private val project: Project) {
  var repositoryDescriptionFile: String = "category.xml"
  var qualifierReplacement: String = SimpleDateFormat("yyyyMMddHHmm").format(Calendar.getInstance().time)
  var createPublication: Boolean = true

  private val featureConfig = project.featureConfig
  internal val featureDependencyToCategoryName = mutableMapOf<Dependency, String>()

  fun feature(group: String, name: String, version: String, categoryName: String? = null): Dependency {
    val dependency = project.dependencies.create(group, name, version, FeatureBasePlugin.feature)
    featureConfig.dependencies.add(dependency)
    if(categoryName != null) {
      featureDependencyToCategoryName[dependency] = categoryName
    }
    return dependency
  }

  fun featureProject(path: String, categoryName: String? = null): Dependency {
    val dependency = project.dependencies.project(path, FeatureBasePlugin.feature)
    featureConfig.dependencies.add(dependency)
    if(categoryName != null) {
      featureDependencyToCategoryName[dependency] = categoryName
    }
    return dependency
  }
}

@Suppress("unused")
class RepositoryPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(FeatureBasePlugin::class)
    project.pluginManager.apply(MavenizeDslPlugin::class)
    project.extensions.add("repository", RepositoryExtension(project))
    project.afterEvaluate { configure(this) }
  }

  private fun configure(project: Project) {
    val log = GradleLog(project.logger)
    val repositoryConfig = project.configurations.create("repository") {
      isVisible = true
      isTransitive = false
      isCanBeConsumed = true
      isCanBeResolved = true
    }
    val featureConfig = project.featureConfig
    val extension = project.extensions.getByType<RepositoryExtension>()

    project.pluginManager.apply(BasePlugin::class)

    project.pluginManager.apply(MavenizePlugin::class)
    val mavenized = project.mavenizedEclipseInstallation()

    // Build repository model from repository description file (category.xml or site.xml) and Gradle project.
    val repository = run {
      val builder = Repository.Builder()
      val repositoryDescriptionFile = project.file(extension.repositoryDescriptionFile).toPath()
      if(Files.isRegularFile(repositoryDescriptionFile)) {
        builder.readFromRepositoryXml(repositoryDescriptionFile, log)
      }
      // Add dependencies from Gradle project.
      for(dependency in featureConfig.allDependencies) {
        if(dependency.version == null) {
          error("Cannot convert dependency $dependency to a feature dependency, as it it has no version")
        }
        val version = MavenVersion.parse(dependency.version!!).toEclipse()
        val categoryName = extension.featureDependencyToCategoryName[dependency]
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
      if(featureConfig.allDependencies.isEmpty()) {
        for(featureDependency in repository.dependencies) {
          val coords = converter.convert(featureDependency.coordinates)
          val isMavenizedBundle = mavenized.isMavenizedBundle(coords.groupId, coords.id)
          // Skip dependencies to mavenized bundles, as they are provided and should not be included in features.
          if(!isMavenizedBundle) {
            val gradleDependency = coords.toGradleDependency(project, featureConfig.name)
            featureConfig.dependencies.add(gradleDependency)
          }
        }
      }
    }

    // Unpack dependency features.
    val unpackFeaturesDir = project.buildDir.resolve("unpackFeatures")
    unpackFeaturesDir.mkdirs()
    val unpackFeaturesTask = project.tasks.create("unpackFeatures") {
      dependsOn(featureConfig)
      inputs.files(featureConfig)
      outputs.dir(unpackFeaturesDir)
      doFirst {
        unpackFeaturesDir.deleteRecursively()
        unpackFeaturesDir.mkdirs()
      }
      doLast {
        project.copy {
          featureConfig.forEach {
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
              val featureFile = unpackTempDir.resolve("feature.xml")
              if(Files.isRegularFile(featureFile)) {
                // TODO: this could have false positives, do a model 2 model transformation instead?
                featureFile.replaceInFile("qualifier", concreteQualifier)
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
                // TODO: this could have false positives, do a model 2 model transformation instead?
                manifestFile.replaceInFile("qualifier", concreteQualifier)
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
      dependsOn(featureConfig)
      inputs.files(featureConfig)
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

    // Add the result of the ZIP task as an artifact in the 'repository' configuration.
    var artifact: PublishArtifact? = null
    project.artifacts {
      artifact = add(repositoryConfig.name, zipRepositoryTask)
    }
    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("Repository") {
            artifact(artifact)
            pom {
              packaging = "repository"
              withXml {
                val root = asElement()
                val doc = root.ownerDocument
                val dependenciesNode = doc.createElement("dependencies")
                for(dependency in featureConfig.dependencies) {
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

                  val packagingNode = doc.createElement("packaging")
                  packagingNode.appendChild(doc.createTextNode("feature"))
                  dependencyNode.appendChild(packagingNode)

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

private fun Path.replaceInFile(pattern: String, replacement: String) {
  // TODO: more efficient way to replace strings in a file?
  val text = String(Files.readAllBytes(this)).replace(pattern, replacement)
  Files.newOutputStream(this).buffered().use { outputStream ->
    PrintStream(outputStream).use {
      it.print(text)
      it.flush()
    }
    outputStream.flush()
  }
}
