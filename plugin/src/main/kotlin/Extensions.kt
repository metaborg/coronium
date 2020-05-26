import mb.coronium.plugin.BundleExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.*

fun Project.eclipse(name: String, version: String? = null): Dependency {
  return extensions.getByType<BundleExtension>().createEclipseTargetPlatformDependency(name, version)
}
