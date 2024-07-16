plugins {
    alias(libs.plugins.metaborg.gradle.rootproject)
    alias(libs.plugins.metaborg.gradle.kotlin.gradleplugin)
    alias(libs.plugins.metaborg.gradle.junit.testing)
    alias(libs.plugins.gitonium)
    `kotlin-dsl`
}

version = gitonium.version
group = "org.metaborg"

repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
    mavenCentral() // Backup
}

gradlePlugin {
    plugins {
        create("coronium-bundle-base") {
            id = "org.metaborg.coronium.bundle.base"
            implementationClass = "mb.coronium.plugin.base.BundleBasePlugin"
        }
        create("coronium-bundle") {
            id = "org.metaborg.coronium.bundle"
            implementationClass = "mb.coronium.plugin.BundlePlugin"
        }

        create("coronium-feature-base") {
            id = "org.metaborg.coronium.feature.base"
            implementationClass = "mb.coronium.plugin.base.FeatureBasePlugin"
        }
        create("coronium-feature") {
            id = "org.metaborg.coronium.feature"
            implementationClass = "mb.coronium.plugin.FeaturePlugin"
        }

        create("coronium-repository-base") {
            id = "org.metaborg.coronium.repository.base"
            implementationClass = "mb.coronium.plugin.base.RepositoryBasePlugin"
        }
        create("coronium-repository") {
            id = "org.metaborg.coronium.repository"
            implementationClass = "mb.coronium.plugin.RepositoryPlugin"
        }
    }
}

// Embed all dependencies into the plugin so that users do not receive the transitive dependency tree.
val embedded: Configuration = configurations.create("embedded")
configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(embedded)
dependencies {
    embedded(libs.maven.resolver.api)
    embedded(libs.maven.resolver.impl)
    embedded(libs.maven.resolver.connector.basic)
    embedded(libs.maven.resolver.transport.file)
    embedded(libs.maven.resolver.provider)
    embedded(libs.commons.compress)
    embedded(libs.bnd.gradle)
}
tasks {
    jar {
        // Closure inside from to defer evaluation of configuration until task execution time.
        from({ embedded.filter { it.exists() }.map { if (it.isDirectory) it else zipTree(it) } }) {
            // Exclude signature files from dependencies, otherwise the JVM will refuse to load the created JAR file.
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        }
        // Enable zip64 to support ZIP files with more than 2^16 entries, which we need.
        isZip64 = true
        // Allow duplicates, as some dependencies have duplicate files/classes.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}
