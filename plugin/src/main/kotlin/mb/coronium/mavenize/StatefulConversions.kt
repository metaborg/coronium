package mb.coronium.mavenize

import mb.coronium.model.eclipse.Bundle
import mb.coronium.model.eclipse.BundleDependency
import mb.coronium.model.eclipse.DependencyResolution
import mb.coronium.model.eclipse.DependencyVisibility
import mb.coronium.model.eclipse.Feature
import mb.coronium.model.eclipse.Repository
import mb.coronium.model.maven.DependencyCoordinates
import mb.coronium.model.maven.MavenArtifact
import mb.coronium.model.maven.MavenDependency

class EclipseToMavenConverter(private val fallbackGroupId: String) {
    // TODO: use name + version as key. Now just matching names, which fails when a bundle has multiple versions.
    private val bundleNameToGroupIds = hashMapOf<String, String>()
    private val fragmentHostNameToGuests = hashMapOf<String, HashSet<Bundle>>()
    private val bundleNameToSourceBundle = hashMapOf<String, Bundle>()


    fun recordBundle(bundle: Bundle, groupId: String) {
        bundleNameToGroupIds[bundle.coordinates.name] = groupId
        if (bundle.fragmentHost != null) {
            val toGuests = fragmentHostNameToGuests.getOrPut(bundle.fragmentHost.name) { hashSetOf() }
            toGuests.add(bundle)
        }
        if (bundle.sourceBundleFor != null) {
            bundleNameToSourceBundle[bundle.sourceBundleFor.name] = bundle
        }
    }

    fun recordGroupId(bundleName: String, groupId: String) {
        bundleNameToGroupIds[bundleName] = groupId
    }


    fun convert(bundle: Bundle): MavenArtifact {
        val groupId = bundleNameToGroupIds[bundle.coordinates.name] ?: fallbackGroupId
        val classifier = if (bundle.sourceBundleFor != null) "sources" else null
        val coordinates = bundle.coordinates.toMaven(groupId, classifier)
        val dependencies = run {
            val dependencies = bundle.requiredBundles.mapNotNull {
                if (it.name == "system.bundle") {
                    // 'system.bundle' refers to a fake/hidden system bundle, so it should be ignored.
                    null
                } else {
                    val depGroupId = bundleNameToGroupIds[it.name] ?: fallbackGroupId
                    it.toMaven(depGroupId)
                }
            }
            val fragmentGuestDependencies = fragmentGuestDependencies(bundle)
            dependencies + fragmentGuestDependencies
        }
        return MavenArtifact(coordinates, dependencies)
    }

    fun convert(coordinates: Feature.BundleInclude.Coordinates): DependencyCoordinates {
        val groupId = bundleNameToGroupIds[coordinates.id] ?: fallbackGroupId
        return coordinates.toMaven(groupId)
    }

    fun convert(dependency: Repository.Dependency.Coordinates): DependencyCoordinates {
        val groupId = bundleNameToGroupIds[dependency.id] ?: fallbackGroupId
        return dependency.toMaven(groupId)
    }


    fun fragmentGuestDependencies(bundle: Bundle): Collection<MavenDependency> {
        val fragmentGuests = fragmentHostNameToGuests[bundle.coordinates.name]
        return fragmentGuests?.map {
            // Emulate a fragment by creating a dependency from the guest to the host, which is mandatory and reexported.
            val (name, version) = it.coordinates
            val groupId = bundleNameToGroupIds[name]
                ?: error("No group ID was set for fragment quest bundle '${it.coordinates}', used from bundle '${bundle.coordinates}")
            val bundleDependency =
                BundleDependency(name, version, DependencyResolution.Mandatory, DependencyVisibility.Reexport, false)
            bundleDependency.toMaven(groupId)
        } ?: listOf()
    }

    fun sourceBundleFor(bundle: Bundle): Bundle? {
        return bundleNameToSourceBundle[bundle.coordinates.name]
    }
}
