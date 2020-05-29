package mb.coronium.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

open class FeatureBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val featureRuntimeUsage = "feature-runtime"
    const val featureRuntimeElements = "featureRuntimeElements"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)

    // Attributes
    val featureRuntimeUsage = objectFactory.named(Usage::class.java, featureRuntimeUsage)

    // Consumable configurations
    project.configurations.create(featureRuntimeElements) {
      description = "Features required when executing in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, featureRuntimeUsage)
    }
  }
}

internal val Project.featureRuntimeElements get(): Configuration = this.configurations.getByName(FeatureBasePlugin.featureRuntimeElements)
