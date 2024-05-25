package mb.coronium.mavenize

import mb.coronium.model.eclipse.BundleCoordinates
import mb.coronium.model.eclipse.BundleDependency
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.BundleVersionOrRange
import mb.coronium.model.eclipse.BundleVersionRange
import mb.coronium.model.eclipse.DependencyResolution
import mb.coronium.model.eclipse.DependencyVisibility
import mb.coronium.model.eclipse.Feature
import mb.coronium.model.eclipse.Repository
import mb.coronium.model.maven.Coordinates
import mb.coronium.model.maven.DependencyCoordinates
import mb.coronium.model.maven.MavenDependency
import mb.coronium.model.maven.MavenVersion
import mb.coronium.model.maven.MavenVersionOrRange
import mb.coronium.model.maven.MavenVersionRange

fun BundleVersion.toMaven(): MavenVersion {
    return MavenVersion.from(major, minor, micro, qualifier?.replace("qualifier", "SNAPSHOT"))
}

fun BundleVersionRange.toMaven(): MavenVersionRange {
    return MavenVersionRange.from(minInclusive, minVersion.toMaven(), maxVersion.toMaven(), maxInclusive)
}

fun BundleVersionOrRange.toMaven(): MavenVersionOrRange {
    return when (this) {
        is BundleVersion -> {
            // Bundle dependency versions mean version *or higher*, so we convert it into a version range from the version to anything.
            MavenVersionRange.from(true, this.toMaven())
        }

        is BundleVersionRange -> toMaven()
    }
}


fun MavenVersion.toEclipse(): BundleVersion {
    return BundleVersion(major, minor, incremental, qualifier?.replace("SNAPSHOT", "qualifier")?.replace("+dirty", ""))
}

fun MavenVersionRange.toEclipse(): BundleVersionOrRange {
    val lower = if (range.lowerBound != null) {
        MavenVersion(range.lowerBound).toEclipse()
    } else {
        BundleVersion.zero()
    }
    if (range.upperBound == null) {
        return lower
    }
    val upper = MavenVersion(range.upperBound).toEclipse()
    return BundleVersionRange(range.isLowerBoundInclusive, lower, upper, range.isUpperBoundInclusive)
}

fun MavenVersionOrRange.toEclipse(): BundleVersionOrRange {
    return when (this) {
        is MavenVersion -> toEclipse()
        is MavenVersionRange -> toEclipse()
    }
}


fun BundleCoordinates.toMaven(groupId: String, classifier: String? = null, extension: String = "jar"): Coordinates {
    val version = version.toMaven()
    return Coordinates(groupId, name, version, classifier, extension)
}

fun Coordinates.toEclipse(): BundleCoordinates {
    val version = version.toEclipse()
    return BundleCoordinates(id, version)
}


fun BundleDependency.toMaven(
    groupId: String,
    classifier: String? = null,
    extension: String? = null
): MavenDependency {
    val version = version?.toMaven()
        ?: MavenVersionRange.any() // No explicit bundle dependency version means *any version*, so we convert it into a version range from '0' to anything.
    val scope = when (visibility) {
        DependencyVisibility.Private -> "provided"
        DependencyVisibility.Reexport -> "compile"
    }
    val optional = resolution == DependencyResolution.Optional
    val coordinates = DependencyCoordinates(groupId, name, version, classifier, extension)
    return MavenDependency(coordinates, scope, optional)
}

fun MavenDependency.toEclipse(): BundleDependency {
    val version = coordinates.version.toEclipse()
    val visibility = when (scope) {
        "compile" -> DependencyVisibility.Reexport
        "provided" -> DependencyVisibility.Private
        else -> DependencyVisibility.Private
    }
    val resolution = if (optional) DependencyResolution.Optional else DependencyResolution.Mandatory
    return BundleDependency(coordinates.id, version, resolution, visibility)
}


fun Feature.BundleInclude.Coordinates.toMaven(
    groupId: String,
    classifier: String? = null,
    extension: String? = null
): DependencyCoordinates {
    if (version == null) {
        throw IllegalStateException("Cannot convert coordinates without version to Maven")
    }
    val version = this.version.toMaven()
    return DependencyCoordinates(groupId, id, version, classifier, extension)
}

fun Repository.Dependency.Coordinates.toMaven(
    groupId: String,
    classifier: String? = null,
    extension: String? = null
): DependencyCoordinates {
    val version = this.version.toMaven()
    return DependencyCoordinates(groupId, id, version, classifier, extension)
}
