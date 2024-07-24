import org.metaborg.convention.MavenPublishConventionExtension

// Workaround for issue: https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("org.metaborg.convention.root-project")

    // Set versions for plugins to use, only applying them in subprojects (apply false here).
    id("org.metaborg.coronium.bundle") apply false // No version: use the plugin from the included composite build
    id("org.metaborg.coronium.feature") apply false
}


allprojects {
    // Disable actual publishing tasks to prevent this repository from being actually published.
    tasks.all {
        if (name.contains("publish") && !name.contains("ToMavenLocal")) {
            enabled = false
        }
    }
}
