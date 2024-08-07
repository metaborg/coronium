plugins {
    alias(libs.plugins.metaborg.gradle.rootproject)
    alias(libs.plugins.gitonium)

    // Set versions for plugins to use, only applying them in subprojects (apply false here).
    id("org.metaborg.coronium.bundle") apply false // No version: use the plugin from the included composite build
    id("org.metaborg.coronium.feature") apply false
}

subprojects {
    metaborg {
        configureSubProject()
    }
}

allprojects {
    // Disable actual publishing tasks to prevent this repository from being actually published.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        enabled = ("publish" !in name || "ToMavenLocal" in name)
    }
}
