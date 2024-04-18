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
        const val repositoryUsage = "repository"
        const val repositoryArchive = "repositoryRuntimeElements"
    }

    override fun apply(project: Project) {
        // Attributes
        val repositoryUsage = objectFactory.named(Usage::class.java, repositoryUsage)

        // Consumable configurations
        project.configurations.create(repositoryArchive) {
            description = "Repository archive"
            isCanBeConsumed = true
            isCanBeResolved = false
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, repositoryUsage)
        }
    }
}

internal val Project.repositoryUsage
    get(): Usage = this.objects.named(
        Usage::class.java,
        RepositoryBasePlugin.repositoryUsage
    )

internal val Project.repositoryArchive get(): Configuration = this.configurations.getByName(RepositoryBasePlugin.repositoryArchive)

