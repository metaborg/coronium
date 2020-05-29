package mb.coronium.plugin.base

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class RepositoryBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory
) : Plugin<Project> {
  companion object {
    const val repositoryRuntimeUsage = "repository-runtime"
    const val repositoryRuntimeElements = "repositoryRuntimeElements"
  }

  override fun apply(project: Project) {
    // Attributes
    val repositoryRuntimeUsage = objectFactory.named(Usage::class.java, repositoryRuntimeUsage)

    // Consumable configurations
    project.configurations.create(repositoryRuntimeElements) {
      description = "Repositories required when executing in the target platform"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      attributes.attribute(Usage.USAGE_ATTRIBUTE, repositoryRuntimeUsage)
    }
  }
}

internal val Project.repositoryRuntimeUsage get(): Usage = this.objects.named(Usage::class.java, RepositoryBasePlugin.repositoryRuntimeUsage)

internal val Project.repositoryRuntimeElements get(): Configuration = this.configurations.getByName(RepositoryBasePlugin.repositoryRuntimeElements)

