import org.metaborg.convention.MavenPublishConventionExtension

plugins {
    id("org.metaborg.convention.root-project")
    alias(libs.plugins.gitonium)
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
}
