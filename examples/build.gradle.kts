import org.metaborg.convention.MavenPublishConventionExtension

// Workaround for issue: https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("org.metaborg.convention.root-project")
    alias(libs.plugins.gitonium)

    // Set versions for plugins to use, only applying them in subprojects (apply false here).
    id("org.metaborg.coronium.bundle") apply false // No version: use the plugin from the included composite build
    id("org.metaborg.coronium.feature") apply false
}

gitonium {
    mainBranch.set("master")
}

allprojects {
    apply(plugin = "org.metaborg.gitonium")
    version = gitonium.version
    group = "org.metaborg"

    pluginManager.withPlugin("org.metaborg.convention.maven-publish") {
        extensions.configure(MavenPublishConventionExtension::class.java) {
            repoOwner.set("metaborg")
            repoName.set("coronium")
        }
    }

    // Disable actual publishing tasks to prevent this repository from being actually published.
    tasks.all {
        if (name.contains("publish") && !name.contains("ToMavenLocal")) {
            enabled = false
        }
    }
}
