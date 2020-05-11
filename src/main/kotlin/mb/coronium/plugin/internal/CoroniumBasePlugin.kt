package mb.coronium.plugin.internal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.*

object ConfigNames {
  const val bundleApi = "bundleApi"
  const val bundleImplementation = "bundleImplementation"
  const val bundleTargetPlatform = "bundleTargetPlatform"

  const val requireBundleReexport = "requireBundleReexport"
  const val requireBundlePrivate = "requireBundlePrivate"
}

class CoroniumBasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Apply Java library plugin to get access to its configurations.
    project.pluginManager.apply(JavaLibraryPlugin::class)
    project.configurations {
      // Register bundle configurations, which are made to be extended by the Java library plugin's configurations.
      // These configurations are intended for users of this plugin to create dependencies to bundles.
      val bundleApi = create(ConfigNames.bundleApi) {
        isVisible = false
        description = "API (external) bundle dependencies."
        isCanBeConsumed = false
        isCanBeResolved = false
        getByName(JavaPlugin.API_CONFIGURATION_NAME).extendsFrom(this)
      }
      create(ConfigNames.bundleImplementation) {
        isVisible = false
        description = "Implementation only (internal) bundle dependencies."
        isCanBeConsumed = false
        isCanBeResolved = false
        getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(this)
        extendsFrom(bundleApi)
      }
      register(ConfigNames.bundleTargetPlatform) {
        isVisible = false
        description = "Target platform (compile-only) bundle dependencies."
        isCanBeConsumed = false
        isCanBeResolved = false
        getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(this)
      }

      // Register internal configurations for creating the Require-Bundle entries of the manifest. These configurations
      // are intended for internal use only: they will be resolved and resolved modules will be added to Require-Bundle.
      register(ConfigNames.requireBundleReexport) {
        isVisible = false
        description = "Re-exported required bundles."
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom()
      }
      register(ConfigNames.requireBundlePrivate) {
        isVisible = false
        description = "Private required bundles."
        isCanBeConsumed = false
        isCanBeResolved = true
        getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(this)
      }
    }
  }
}

internal fun Project.bundleImplementationConfig(): Configuration = this.configurations.getByName(ConfigNames.bundleImplementation)
internal fun Project.bundleApiConfig(): Configuration = this.configurations.getByName(ConfigNames.bundleApi)
internal fun Project.bundleTargetPlatformConfig(): Configuration = this.configurations.getByName(ConfigNames.bundleTargetPlatform)

internal fun Project.requireBundleReexportConfig(): Configuration = this.configurations.getByName(ConfigNames.requireBundleReexport)
internal fun Project.requireBundlePrivateConfig(): Configuration = this.configurations.getByName(ConfigNames.requireBundlePrivate)

internal fun Project.bundleRuntimeClasspathConfig(): Configuration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

//fun Project.bundleCompileConfig(reexport: Boolean = false) =
//  this.configurations.getByName(if(reexport) ConfigNames.bundleApi else ConfigNames.bundleImplementation)
//
//fun Project.bundleCompileConfigs() =
//  sequenceOf(bundleCompileConfig(false), bundleCompileConfig(true))
//
//fun Project.bundleCompileDeps() =
//  bundleCompileConfigs().flatMap { it.dependencies.asSequence() }
//
//fun Project.bundleCompileFiles() =
//  bundleCompileConfigs().flatMap { it.asSequence() }
//
//
//fun Project.bundleRuntimeConfig() = this.configurations.getByName(ConfigNames.bundleCompileOnly)
//
//fun Project.bundleRuntimeDeps() = bundleRuntimeConfig().dependencies
//
//fun Project.bundleRuntimeFiles() = bundleRuntimeConfig().asSequence()
