rootProject.name = "coronium.root"

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
    }
}

plugins {
    id("org.metaborg.convention.settings") version "0.6.12"
}

// We split the build up into one main composite build in the 'plugin' directory, because it builds Gradle plugins,
// which we want to test. Gradle plugins are not directly available in a multi-project build, therefore a separate
// composite build is required.
includeBuild("coronium/")
// Included builds listed below can use the Gradle plugins built in 'coronium'.
includeBuild("examples/")

