rootProject.name = "coronium.root"

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
    }
}

// This allows us to use the catalog in dependencies
dependencyResolutionManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
    }
    versionCatalogs {
        create("libs") {
            from("org.metaborg.spoofax3:catalog:0.2.3")
        }
    }
}

// We split the build up into one main composite build in the 'plugin' directory, because it builds Gradle plugins,
// which we want to test. Gradle plugins are not directly available in a multi-project build, therefore a separate
// composite build is required.
includeBuildWithName("plugin", "coronium")
// Included builds listed below can use the Gradle plugins built in 'plugin'.
includeBuildWithName("example", "coronium.example")

fun includeBuildWithName(dir: String, name: String) {
    includeBuild(dir) {
        try {
            ConfigurableIncludedBuild::class.java
                .getDeclaredMethod("setName", String::class.java)
                .invoke(this, name)
        } catch (e: NoSuchMethodException) {
            // Running Gradle < 6, no need to set the name, ignore.
        }
    }
}
