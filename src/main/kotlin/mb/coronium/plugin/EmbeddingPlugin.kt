package mb.coronium.plugin

import mb.coronium.util.eclipseVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*

@Suppress("unused")
open class EmbeddingExtension(private val project: Project) {
//  var createPublication: Boolean = false

  val bundleVersion: String get() = project.eclipseVersion.toString()
}

@Suppress("unused")
class EmbeddingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = EmbeddingExtension(project)
    project.extensions.add("embedding", extension)

//    project.pluginManager.apply(BundleBasePlugin::class)


    // Make the bundle dependency configurations extend Java's dependency configurations, such that Java dependencies get included as bundle
    // dependencies.
//    project.pluginManager.apply(JavaLibraryPlugin::class)
//    project.bundleConfig.extendsFrom(project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME))

//     Add the result of the JAR task as an artifact in the 'bundle' configuration.
//    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
//    project.artifacts {
//      add(BundleBasePlugin.bundle, jarTask)
//    }
//    if(extension.createPublication) {
//      // Add Java component as main publication.
//      project.pluginManager.withPlugin("maven-publish") {
//        val component = project.components.getByName("java")
//        project.extensions.configure<PublishingExtension> {
//          publications.create<MavenPublication>("Bundle") {
//            from(component)
//          }
//        }
//      }
//    }
  }
}

