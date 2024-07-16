import org.metaborg.convention.MavenPublishConventionExtension

plugins {
    alias(libs.plugins.metaborg.gradle.rootproject)
    alias(libs.plugins.gitonium)
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
}
