package mb.coronium.plugin.base

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class FeatureBasePlugin @Inject constructor(
    private val objectFactory: ObjectFactory
) : Plugin<Project> {
    companion object {
        const val featureUsage = "feature"
        const val featureElements = "featureElements"
    }

    override fun apply(project: Project) {
        // Attributes
        val featureUsage = objectFactory.named(Usage::class.java, featureUsage)

        // Consumable configurations
        project.configurations.create(featureElements) {
            description = "Features elements"
            isCanBeConsumed = true
            isCanBeResolved = false
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, featureUsage)
        }
    }
}

internal val Project.featureUsage get(): Usage = this.objects.named(Usage::class.java, FeatureBasePlugin.featureUsage)

internal val Project.featureElements get(): Configuration = this.configurations.getByName(FeatureBasePlugin.featureElements)
