package mb.coronium.plugin.internal

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

open class CoroniumBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val bundleApi = "bundleApi"
    const val bundleImplementation = "bundleImplementation"
    const val bundleEmbedApi = "bundleEmbedApi"
    const val bundleEmbedImplementation = "bundleEmbedImplementation"
    const val bundleTargetPlatformApi = "bundleTargetPlatformApi"
    const val bundleTargetPlatformImplementation = "bundleTargetPlatformImplementation"

    const val bundleRuntimeElements = "bundleRuntimeElements"

    const val bundleRuntimeClasspath = "bundleRuntimeClasspath"
    const val bundleEmbedClasspath = "bundleEmbedClasspath"
    const val requireBundleReexport = "requireBundleReexport"
    const val requireBundlePrivate = "requireBundlePrivate"

    const val bundleRuntimeUsage = "bundle-runtime"
  }

  override fun apply(project: Project) {
    // Apply Java library plugin to get access to its configurations.
    project.pluginManager.apply(JavaLibraryPlugin::class)


    // Attributes
    val bundleRuntime = objectFactory.named(Usage::class.java, "bundle-runtime")


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


    // Consumable configurations
    val bundleRuntimeElements = project.configurations.create(bundleRuntimeElements) {
      description = "Bundles required when executing this bundle in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntime)
      outgoing.artifact(project.tasks.getByName<Jar>(JavaPlugin.JAR_TASK_NAME))
    }


    // Internal (resolvable) configurations
    val bundleRuntimeClasspath = project.configurations.create(bundleRuntimeClasspath) {
      description = "Classpath for executing this bundle in the target platform"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      extendsFrom(bundleApi, bundleImplementation)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntime)
    }
    val bundleEmbedClasspath = project.configurations.create(Companion.bundleEmbedClasspath) {
      description = "Classpath for JARs to embed into the bundle"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      extendsFrom(bundleEmbedImplementation)
      //attributes.attribute(Usage.USAGE_ATTRIBUTE, bundleRuntime)
    }
    val requireBundleReexport = project.configurations.create(requireBundleReexport) {
      description = "Require-Bundle dependencies with reexport visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(bundleApi, bundleTargetPlatformApi)
    }
    val requireBundlePrivate = project.configurations.create(requireBundlePrivate) {
      description = "Require-Bundle dependencies with private visibility"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Does not need to be transitive, only interested in direct dependencies.
      extendsFrom(bundleImplementation, bundleTargetPlatformImplementation)
    }

    // Connection to Java configurations.
    project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).extendsFrom(bundleApi, bundleImplementation, bundleEmbedImplementation, bundleTargetPlatformApi, bundleTargetPlatformImplementation)
    project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME).extendsFrom(bundleApi, bundleEmbedApi, bundleTargetPlatformApi)


    // Add our variants to the Java component.
    @Suppress("UnstableApiUsage") run {
      val javaComponent = project.components.findByName("java") as AdhocComponentWithVariants
      javaComponent.addVariantsFromConfiguration(bundleRuntimeElements) {
        mapToMavenScope("runtime")
      }
    }
  }
}

internal fun Project.bundleApi(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleApi)
internal fun Project.bundleImplementation(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleImplementation)
internal fun Project.bundleEmbedApi(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleEmbedApi)
internal fun Project.bundleEmbedImplementation(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleEmbedImplementation)
internal fun Project.bundleTargetPlatformApi(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleTargetPlatformApi)
internal fun Project.bundleTargetPlatformImplementation(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleTargetPlatformImplementation)

internal fun Project.bundleRuntimeElements(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleRuntimeElements)

internal fun Project.bundleRuntimeClasspath(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleRuntimeClasspath)
internal fun Project.bundleEmbedClasspath(): Configuration = this.configurations.getByName(CoroniumBasePlugin.bundleEmbedClasspath)
internal fun Project.requireBundleReexport(): Configuration = this.configurations.getByName(CoroniumBasePlugin.requireBundleReexport)
internal fun Project.requireBundlePrivate(): Configuration = this.configurations.getByName(CoroniumBasePlugin.requireBundlePrivate)
