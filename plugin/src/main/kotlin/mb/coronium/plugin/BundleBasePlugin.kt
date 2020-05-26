package mb.coronium.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.*

open class BundleBasePlugin : Plugin<Project> {
  companion object {
    const val bundleEmbedApi = "bundleEmbedApi"
    const val bundleEmbedImplementation = "bundleEmbedImplementation"
    const val bundleTargetPlatformApi = "bundleTargetPlatformApi"
    const val bundleTargetPlatformImplementation = "bundleTargetPlatformImplementation"

    const val bundleEmbedClasspath = "bundleEmbedClasspath"
    const val requireBundleReexport = "requireBundleReexport"
    const val requireBundlePrivate = "requireBundlePrivate"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)


    // User-facing configurations
    val bundleEmbedApi = project.configurations.create(bundleEmbedApi) {
      description = "API dependencies to Java libraries embedded into the bundle, with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleEmbedImplementation = project.configurations.create(bundleEmbedImplementation) {
      description = "Implementation dependencies to Java libraries embedded into the bundle, with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
      extendsFrom(bundleEmbedApi)
    }
    val bundleTargetPlatformApi = project.configurations.create(bundleTargetPlatformApi) {
      description = "API dependencies to target platform bundles with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val bundleTargetPlatformImplementation = project.configurations.create(bundleTargetPlatformImplementation) {
      description = "Implementation dependencies to target platform bundles with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }


    // Internal (resolvable) configurations
    project.configurations.create(bundleEmbedClasspath) {
      description = "Classpath for JARs to embed into the bundle"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      extendsFrom(bundleEmbedImplementation)
    }
    project.configurations.create(requireBundleReexport) {
      description = "Require-Bundle dependencies with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(project.bundleApi, bundleTargetPlatformApi)
    }
    project.configurations.create(requireBundlePrivate) {
      description = "Require-Bundle dependencies with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(project.bundleImplementation, bundleTargetPlatformImplementation)
    }
  }
}

internal val Project.bundleEmbedApi get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleEmbedApi)
internal val Project.bundleEmbedImplementation get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleEmbedImplementation)
internal val Project.bundleTargetPlatformApi get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleTargetPlatformApi)
internal val Project.bundleTargetPlatformImplementation get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleTargetPlatformImplementation)

internal val Project.bundleEmbedClasspath get(): Configuration = this.configurations.getByName(BundleBasePlugin.bundleEmbedClasspath)
internal val Project.requireBundleReexport get(): Configuration = this.configurations.getByName(BundleBasePlugin.requireBundleReexport)
internal val Project.requireBundlePrivate get(): Configuration = this.configurations.getByName(BundleBasePlugin.requireBundlePrivate)
