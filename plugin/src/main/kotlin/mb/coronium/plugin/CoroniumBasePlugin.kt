package mb.coronium.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class CoroniumBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val bundleRuntimeUsage = "bundle-runtime"
    const val bundleApi = "bundleApi"
    const val bundleImplementation = "bundleImplementation"
    const val bundleRuntimeElements = "bundleRuntimeElements"
    const val bundleRuntimeClasspath = "bundleRuntimeClasspath"
  }

  override fun apply(project: Project) {
    // Attributes
    val bundleRuntimeUsage = objectFactory.named(Usage::class.java, bundleRuntimeUsage)

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

    // Consumable configurations
    project.configurations.create(bundleRuntimeElements) {
      description = "Bundles required when executing this bundle in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntimeUsage)
    }

    // Internal (resolvable) configurations
    project.configurations.create(bundleRuntimeClasspath) {
      description = "Classpath for executing this bundle in the target platform"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntimeUsage)
    }
  }
}

internal val Project.bundleApi get(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleApi)
internal val Project.bundleImplementation get(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleImplementation)
internal val Project.bundleRuntimeElements get(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleRuntimeElements)
internal val Project.bundleRuntimeClasspath get(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleRuntimeClasspath)
