package mb.coronium.plugin

import mb.coronium.plugin.internal.BundleBasePlugin
import mb.coronium.plugin.internal.bundleConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByName

@Suppress("unused")
class EmbeddingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(BundleBasePlugin::class)

    // Make the bundle dependency configurations extend Java's dependency configurations, such that Java dependencies get included as bundle
    // dependencies.
    project.pluginManager.apply(JavaLibraryPlugin::class)
    project.bundleConfig.extendsFrom(project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME))

    // Publish the result of the JAR task in the 'bundle' configuration.
    val jarTask = project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME)
    project.artifacts {
      add(BundleBasePlugin.bundle, jarTask)
    }
  }
}
