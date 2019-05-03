package mb.coronium.plugin.internal

import org.gradle.api.Plugin
import org.gradle.api.Project

object ConfigNames {
  const val bundleCompile = "bundleCompile"
  const val bundleCompileReexport = "bundleCompileReexport"

  const val bundleRuntime = "bundleRuntime"
}

class CoroniumBasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.configurations.create(ConfigNames.bundleCompile) {
      isTransitive = false
    }
    project.configurations.create(ConfigNames.bundleCompileReexport) {
      isTransitive = true
    }

    project.configurations.create(ConfigNames.bundleRuntime) {
      isTransitive = true
    }
  }
}

fun Project.bundleCompileConfig(reexport: Boolean = false) =
  this.configurations.getByName(if(reexport) ConfigNames.bundleCompileReexport else ConfigNames.bundleCompile)

fun Project.bundleCompileConfigs() =
  sequenceOf(bundleCompileConfig(false), bundleCompileConfig(true))

fun Project.bundleCompileDeps() =
  bundleCompileConfigs().flatMap { it.dependencies.asSequence() }

fun Project.bundleCompileFiles() =
  bundleCompileConfigs().flatMap { it.asSequence() }


fun Project.bundleRuntimeConfig() = this.configurations.getByName(ConfigNames.bundleRuntime)

fun Project.bundleRuntimeDeps() = bundleRuntimeConfig().dependencies

fun Project.bundleRuntimeFiles() = bundleRuntimeConfig().asSequence()