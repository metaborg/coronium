rootProject.name = "coronium.example"

// This allows us to use plugins from Metaborg Artifacts
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
            from("org.metaborg.spoofax3:catalog:0.2.2")
        }
    }
}

// This downloads an appropriate JVM if not already available
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}


// Only include composite builds when this is the root project (it has no parent), for example when running Gradle tasks
// from the command-line. Otherwise, the parent project will include these composite builds.
if (gradle.parent == null) {
    includeBuild("../plugin")
}

fun String.includeProject(id: String, path: String = "$this/$id") {
    include(id)
    project(":$id").projectDir = file(path)
}

"complex".run {
    includeProject("complex.platform")
    includeProject("complex.spoofax")
    includeProject("complex.spoofax.eclipse")
    includeProject("complex.spoofax.eclipse.feature")
    includeProject("complex.tiger")
    includeProject("complex.tiger.eclipse")
    includeProject("complex.tiger.eclipse.feature")
    includeProject("complex.tiger.eclipse.repository")
}
