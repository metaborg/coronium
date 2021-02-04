package mb.coronium.plugin.base

import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.lazilyMavenize
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

open class BundleBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val bundleUsage = "bundle"

    const val bundleElements = "bundleElements"
    const val bundleRuntimeClasspath = "bundleRuntimeClasspath"
  }

  override fun apply(project: Project) {
    // Attributes
    val bundleUsage = objectFactory.named(Usage::class.java, bundleUsage)

    // Consumable configurations
    project.configurations.create(bundleElements) {
      description = "Bundle elements"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleUsage)
    }

    // Internal (resolvable) configurations
    project.pluginManager.apply(MavenizePlugin::class)
    val bundleRuntimeClasspathConfiguration = project.configurations.register(bundleRuntimeClasspath) {
      description = "Classpath for executing this bundle in the target platform"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleUsage)
    }
    bundleRuntimeClasspathConfiguration.configure { withDependencies { project.lazilyMavenize() } }
  }
}

internal val Project.bundleUsage get(): Usage = this.objects.named(Usage::class.java, BundleBasePlugin.bundleUsage)

internal val Project.bundleElements get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleElements)
internal val Project.bundleRuntimeClasspath get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleRuntimeClasspath)
