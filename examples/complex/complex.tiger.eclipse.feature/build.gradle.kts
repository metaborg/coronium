plugins {
    id("org.metaborg.coronium.feature")
}

// This is a copy of dependencyManagement in the root project's settings.gradle.kts,
//  which is needed because the Mavenize plugin (via Coronium) defines its own repository,
//  overriding those defined in the root dependencyManagement.
repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
    mavenCentral()
}

dependencies {
    featureInclude(project(":complex.spoofax.eclipse.feature"))
    bundle(project(":complex.tiger.eclipse")) {
        // Including a bundle into a feature also includes all reexported bundles. In this case, we want to prevent this
        // because 'complex.spoofax.eclipse' is included into the 'complex.spoofax.eclipse.feature' feature.
        exclude("org.metaborg", "complex.spoofax.eclipse")
    }
}
