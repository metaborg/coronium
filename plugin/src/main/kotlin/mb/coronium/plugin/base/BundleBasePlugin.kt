package mb.coronium.plugin.base

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class BundleBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val bundleRuntimeUsage = "bundle-runtime"

    const val bundleRuntimeElements = "bundleRuntimeElements"
    const val bundleRuntimeClasspath = "bundleRuntimeClasspath"
  }

  override fun apply(project: Project) {
    // Attributes
    val bundleRuntimeUsage = objectFactory.named(Usage::class.java, bundleRuntimeUsage)

    // Consumable configurations
    project.configurations.create(bundleRuntimeElements) {
      description = "Bundles required when executing this bundle in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      // TODO: extend in bundle plugin and other plugins where needed
      //extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntimeUsage)
    }

    // Internal (resolvable) configurations
    project.configurations.create(bundleRuntimeClasspath) {
      description = "Classpath for executing this bundle in the target platform"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      // TODO: extend in bundle plugin and other plugins where needed
      //extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntimeUsage)
    }
  }
}

internal val Project.bundleRuntimeUsage get(): Usage = this.objects.named(Usage::class.java, BundleBasePlugin.bundleRuntimeUsage)

internal val Project.bundleRuntimeElements get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleRuntimeElements)
internal val Project.bundleRuntimeClasspath get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleRuntimeClasspath)
