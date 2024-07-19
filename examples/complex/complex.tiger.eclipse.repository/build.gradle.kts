plugins {
    id("org.metaborg.coronium.repository")
    `maven-publish`
}

repository {
    eclipseInstallationAppName.set("Tiger")
    // Disabled because it increases the CI build times a lot.
//  createEclipseInstallationPublications.set(true)
//  createEclipseInstallationWithJvmPublications.set(true)
}

// This is a copy of dependencyManagement in the root project's settings.gradle.kts,
//  which is needed because the Mavenize plugin defined its own repository,
//  overriding those defined in the root dependencyManagement.
repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
    mavenCentral()
}

dependencies {
    feature(project(":complex.tiger.eclipse.feature"))
}
