package mb.coronium.plugin.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

class FeatureBasePlugin : Plugin<Project> {
  companion object {
    const val feature = "eclipseFeature"
  }

  override fun apply(project: Project) {
    project.configurations.create(feature) {
      isVisible = true
      isTransitive = false
      isCanBeConsumed = true
      isCanBeResolved = true
    }
  }
}

val Project.featureConfig get() = this.configurations.getByName(FeatureBasePlugin.feature)
