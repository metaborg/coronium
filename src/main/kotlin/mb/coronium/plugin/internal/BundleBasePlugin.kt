package mb.coronium.plugin.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

class BundleBasePlugin : Plugin<Project> {
  companion object {
    const val bundleApi = "bundleApi"
    const val bundleApiProvided = "bundleApiProvided"
    const val bundleImplementation = "bundleImplementation"
    const val bundleImplementationProvided = "bundleImplementationProvided"
    const val bundle = "bundle"
  }

  override fun apply(project: Project) {
    val bundleApiConfig = project.configurations.create(bundleApi) {
      isVisible = false
      isTransitive = true
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    val bundleApiProvidedConfig = project.configurations.create(bundleApiProvided) {
      isVisible = false
      isTransitive = true
      isCanBeConsumed = false
      isCanBeResolved = false
      extendsFrom(bundleApiConfig)
    }

    val bundleImplementationConfig = project.configurations.create(bundleImplementation) {
      isVisible = false
      isTransitive = true
      isCanBeConsumed = false
      isCanBeResolved = false
      extendsFrom(bundleApiConfig)
    }
    val bundleImplementationProvidedConfig = project.configurations.create(bundleImplementationProvided) {
      isVisible = false
      isTransitive = true
      isCanBeConsumed = false
      isCanBeResolved = false
      extendsFrom(bundleImplementationConfig)
    }

    project.configurations.create(bundle) {
      isVisible = true
      isTransitive = true
      isCanBeConsumed = true
      isCanBeResolved = true
      extendsFrom(bundleApiConfig, bundleApiProvidedConfig, bundleImplementationConfig, bundleImplementationProvidedConfig)
    }
  }
}

val Project.bundleApiConfig get() = this.configurations.getByName(BundleBasePlugin.bundleApi)
val Project.bundleApiProvidedConfig get() = this.configurations.getByName(BundleBasePlugin.bundleApiProvided)
val Project.bundleImplementationConfig get() = this.configurations.getByName(BundleBasePlugin.bundleImplementation)
val Project.bundleImplementationProvidedConfig get() = this.configurations.getByName(BundleBasePlugin.bundleImplementationProvided)
val Project.bundleConfig get() = this.configurations.getByName(BundleBasePlugin.bundle)
