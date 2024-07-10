package mb.coronium.util

import mb.coronium.model.maven.DependencyCoordinates
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.create

fun DependencyCoordinates.toGradleDependency(
    project: Project,
    configuration: String? = Dependency.DEFAULT_CONFIGURATION
) =
    project.dependencies.create(groupId, id, version.toString(), configuration, classifier, extension)

fun DependencyCoordinates.toGradleDependencyNotation() =
    "$groupId:$id:$version${if (classifier != null) ":$classifier" else ""}${if (extension != null) "@$extension" else ""}"
