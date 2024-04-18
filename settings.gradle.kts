rootProject.name = "coronium.root"

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
    }
}


// Only include composite builds when this is the root project (it has no parent). Otherwise, the parent project will
// include these composite builds, as IntelliJ does not support nested composite builds.
if (gradle.parent == null) {
    // We split the build up into one main composite build in the 'plugin' directory, because it builds Gradle plugins,
    // which we want to test. Gradle plugins are not directly available in a multi-project build, therefore a separate
    // composite build is required.
    includeBuildWithName("plugin", "coronium")
    // Included builds listed below can use the Gradle plugins built in 'plugin'.
    includeBuildWithName("example", "coronium.example")
}

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
