package mb.coronium.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

open class RepositoryBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val repositoryRuntimeUsage = "repository-runtime"
    const val feature = "feature"
    const val repositoryRuntimeElements = "repositoryRuntimeElements"
    const val repositoryFeatureArtifacts = "repositoryFeatureArtifacts"
    const val repositoryBundleArtifacts = "repositoryBundleArtifacts"
  }

  override fun apply(project: Project) {
    project.pluginManager.apply(CoroniumBasePlugin::class)
    project.pluginManager.apply(FeatureBasePlugin::class)

    // Attributes
    val repositoryRuntimeUsage = objectFactory.named(Usage::class.java, repositoryRuntimeUsage)

    // User-facing configurations
    val feature = project.configurations.create(feature) {
      description = "Feature dependencies to be included in the repository"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }

    // Consumable configurations
    project.configurations.create(repositoryRuntimeElements) {
      description = "Repositories required when executing in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, repositoryRuntimeUsage)
    }

    // Internal (resolvable) configurations
    project.configurations.create(repositoryFeatureArtifacts) {
      description = "Feature artifacts that should end up in the repository"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage::class.java, FeatureBasePlugin.featureRuntimeUsage))
      extendsFrom(feature)
    }
    project.configurations.create(repositoryBundleArtifacts) {
      description = "Bundle artifacts that should end up in the repository"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, objectFactory.named(Usage::class.java, CoroniumBasePlugin.bundleRuntimeUsage))
      extendsFrom(feature)
    }
  }
}

internal val Project.repositoryRuntimeElements get(): Configuration = this.configurations.getByName(RepositoryBasePlugin.repositoryRuntimeElements)
internal val Project.repositoryFeatureArtifacts get(): Configuration = this.configurations.getByName(RepositoryBasePlugin.repositoryFeatureArtifacts)
internal val Project.repositoryBundleArtifacts get(): Configuration = this.configurations.getByName(RepositoryBasePlugin.repositoryBundleArtifacts)
