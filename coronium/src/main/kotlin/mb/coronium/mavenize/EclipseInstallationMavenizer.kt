package mb.coronium.mavenize

import mb.coronium.model.eclipse.Bundle
import mb.coronium.model.eclipse.BundleCoordinates
import mb.coronium.model.eclipse.BundleDependency
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.BundleVersionOrRange
import mb.coronium.model.eclipse.BundleVersionRange
import mb.coronium.util.Log
import mb.coronium.util.TempDir
import mb.coronium.util.deleteNonEmptyDirectoryIfExists
import mb.coronium.util.downloadAndUnarchive
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

fun mavenizeEclipseInstallation(
    mavenizeDir: Path,
    installationArchiveUrl: String,
    installationPluginsDirRelative: Path,
    installationConfigurationDirRelative: Path,
    groupId: String,
    log: Log,
    forceDownload: Boolean = false,
    forceInstall: Boolean = false
): MavenizedEclipseInstallation {
    // Setup paths.
    val repoDir = mavenizeDir.resolve("repo")
    val repoGroupIdDir = repoDir.resolve(groupId)
    val archiveCacheDir = mavenizeDir.resolve("eclipse_archive_cache")

    // Retrieve an Eclipse installation.
    val (installationDir, wasUnarchived) = downloadAndUnarchive(
        installationArchiveUrl,
        archiveCacheDir,
        forceDownload,
        log
    )
    val installationPluginsDir = installationDir.resolve(installationPluginsDirRelative)

    // Check if repository is already installed.
    val isInstalled = Files.isDirectory(repoGroupIdDir)

    // Install bundles if needed.
    if (wasUnarchived || !isInstalled || forceInstall) {
        // Delete repository entries for the group ID.
        deleteNonEmptyDirectoryIfExists(repoGroupIdDir)

        // Read bundles and pre-process them.
        val bundles = MavenInstallableBundle.readAll(installationPluginsDir, groupId, log).map { installableBundle ->
            val (bundle, bundleGroupId, location) = installableBundle

            // Remove qualifiers to fix version range matching in Maven and Gradle.
            val newCoordinates = bundle.coordinates.run {
                BundleCoordinates(name, version.withoutQualifier(), isSingleton)
            }

            fun BundleVersionOrRange?.fixDepVersion() = when (this) {
                null -> null
                is BundleVersion -> withoutQualifier()
                is BundleVersionRange -> withoutQualifiers()
            }

            fun BundleDependency.fixDep() =
                BundleDependency(name, version.fixDepVersion(), resolution, visibility, isSourceBundleDependency)

            val newDeps = bundle.requiredBundles.map { it.fixDep() }
            val newFragmentHost = bundle.fragmentHost?.fixDep()
            val newSourceBundleFor = bundle.sourceBundleFor?.fixDep()
            val newBundle = Bundle(bundle.manifestVersion, newCoordinates, newDeps, newFragmentHost, newSourceBundleFor)
            MavenInstallableBundle(newBundle, bundleGroupId, location)
        }

        // Convert Eclipse bundles to Maven artifacts and install them.
        TempDir("toInstallableMavenArtifacts").use { tempDir ->
            val converter = EclipseBundleToInstallableMavenArtifact(tempDir, groupId)
            val artifacts = converter.convertAll(bundles, log)
            val installer = MavenArtifactInstaller(repoDir)
            installer.installAll(artifacts, log)
        }
    }

    // Collect names of Eclipse installation bundles by directory listing of all installed artifacts.
    val installedBundleDirs = Files.list(repoGroupIdDir)
    val installationBundleNames = installedBundleDirs.map { it.fileName.toString() }.collect(Collectors.toSet())
    installedBundleDirs.close()

    val installationConfigurationDir = installationDir.resolve(installationConfigurationDirRelative)
    return MavenizedEclipseInstallation(
        groupId,
        repoDir,
        installationDir,
        installationPluginsDir,
        installationConfigurationDir,
        installationBundleNames
    )
}

data class MavenizedEclipseInstallation(
    val groupId: String,
    val repoDir: Path,
    val installationDir: Path,
    val installationPluginsDir: Path,
    val installationConfigurationDir: Path,
    val installationBundleNames: Set<String>
) {
    fun isMavenizedBundle(groupId: String, id: String) = groupId == this.groupId && installationBundleNames.contains(id)

    fun createConverter(fallbackGroupId: String): EclipseToMavenConverter {
        val converter = EclipseToMavenConverter(fallbackGroupId)
        for (bundleName in installationBundleNames) {
            converter.recordGroupId(bundleName, groupId)
        }
        return converter
    }


    fun osgiFrameworkPath() = pluginPath("org.eclipse.osgi")

    fun equinoxLauncherPath() = pluginPath("org.eclipse.equinox.launcher")

    fun equinoxConfiguratorPath() = pluginPath("org.eclipse.equinox.simpleconfigurator")

    fun pluginPath(name: String): Path? {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${name}_*.jar")
        val plugins = Files.list(installationPluginsDir)
        val plugin = plugins.filter {
            matcher.matches(it.fileName)
        }.findFirst()
        return if (!plugin.isPresent) {
            null
        } else {
            plugin.get()
        }
    }


    fun equinoxConfiguratorBundlesInfoPath(): Path =
        installationConfigurationDir.resolve("org.eclipse.equinox.simpleconfigurator/bundles.info")
}
