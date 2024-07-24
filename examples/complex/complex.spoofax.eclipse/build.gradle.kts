plugins {
    `java-library`
    id("org.metaborg.convention.java")
    id("org.metaborg.coronium.bundle")
}

// This is a copy of dependencyManagement in the root project's settings.gradle.kts,
//  which is needed because the Mavenize plugin (via Coronium) defines its own repository,
//  overriding those defined in the root dependencyManagement.
repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
    mavenCentral()
}

dependencies {
    api(platform(project(":complex.platform")))
    annotationProcessor(platform(project(":complex.platform")))

    bundleTargetPlatformApi(eclipse("javax.inject"))
    bundleTargetPlatformApi(eclipse("org.eclipse.core.runtime"))
    bundleTargetPlatformApi(eclipse("org.eclipse.ui"))

    bundleEmbedImplementation("org.metaborg:log.backend.slf4j")
    bundleEmbedImplementation("org.slf4j:slf4j-simple")
    bundleEmbedApi(project(":complex.spoofax"))

    compileOnly(libs.checkerframework.android)

    annotationProcessor(libs.dagger.compiler)
}

// For Java libraries that are embedded into the bundle, and are exported (i.e., bundleEmbedApi), we need to add an
// Export-Package directive to the JAR manifest that determines which packages should be exported. Only classes from
// these packages will be embedded. The BND plugin will perform the embedding. Therefore, the Export-Package
// syntax from BND is supported: https://bnd.bndtools.org/heads/export_package.html
val exportPackage = listOf(
    // Regular packages to be exported. Note that this export cannot be written in META-INF/MANIFEST.MF, otherwise its
    // Export-Package directive would overwrite this one, leading to embedded dependencies not being exported.
    "mb.complex.spoofax.eclipse",
    // Embedded packages to be exported. Using ';provider=mb;mandatory:=provider' to prevent these packages from being
    // imported with a regular Import-Package directive. They can only be used with a Require-Bundle dependency to this
    // bundle, or by qualifying an Import-Package directive with ';provider=mb'.
    "mb.complex.spoofax.*;provider=mb;mandatory:=provider",
    "mb.log.api.*;provider=mb;mandatory:=provider",
    "mb.pie.*;provider=mb;mandatory:=provider",
    "dagger.*;provider=mb;mandatory:=provider"
)
// Likewise, for Java libraries that are embedded into the bundle, but not exported (i.e., bundleEmbedImplementation),
// we need to add a Private-Package directive to the JAR manifest that determines which packages should be included.
// Only classes from these packages will be embedded. Again, the BND plugin will perform the embedding. Therefore, the
// Private-Package syntax from BND is supported: https://bnd.bndtools.org/heads/private_package.html
val privatePackage = listOf(
    "mb.log.slf4j.*",
    "org.slf4j.*"
)
tasks {
    "jar"(Jar::class) {
        manifest {
            attributes(
                // Pass the above lists as the Export-Package and Private-Package directives of the JAR manifest.
                Pair("Export-Package", exportPackage.joinToString(", ")),
                Pair("Private-Package", privatePackage.joinToString(", "))
            )
        }
    }
}
