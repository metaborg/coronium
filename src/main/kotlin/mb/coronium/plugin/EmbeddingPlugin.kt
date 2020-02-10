package mb.coronium.plugin

import mb.coronium.util.eclipseVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
open class EmbeddingExtension(private val project: Project) {
  val bundleVersion: String get() = project.eclipseVersion.toString()
}

@Suppress("unused")
class EmbeddingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = EmbeddingExtension(project)
    project.extensions.add("embedding", extension)
  }
}
