rootProject.name = "coronium.example"

dependencyResolutionManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
        gradlePluginPortal()
    }
}

plugins {
    id("org.metaborg.convention.settings") version "0.7.2"
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
