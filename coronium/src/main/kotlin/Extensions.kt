import mb.coronium.plugin.BundleExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

fun Project.eclipse(name: String, version: String? = null): Any {
    return extensions.getByType<BundleExtension>().createEclipseTargetPlatformDependency(name, version)
}
