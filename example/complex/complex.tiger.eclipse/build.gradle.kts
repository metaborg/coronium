plugins {
  id("org.metaborg.gradle.config.java-library")
  id("org.metaborg.coronium.bundle")
}

dependencies {
  api(platform(project(":complex.platform")))
  annotationProcessor(platform(project(":complex.platform")))

  bundleTargetPlatformApi(eclipse("javax.inject"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.runtime"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui"))

  bundleApi(project(":complex.spoofax.eclipse"))
  bundleEmbedApi(project(":complex.tiger"))

  compileOnly("org.checkerframework:checker-qual-android")

  annotationProcessor("com.google.dagger:dagger-compiler")
}

// For Java libraries that are embedded into the bundle, and are exported (i.e., bundleEmbedApi), we need to add an
// Export-Package directive to the JAR manifest that determines which packages should be exported. Only classes from
// these packages will be embedded. The BND plugin will perform the embedding. Therefore, the Export-Package
// syntax from BND is supported: https://bnd.bndtools.org/heads/export_package.html
val exportPackage = listOf(
  // Regular packages to be exported. Note that this export cannot be written in META-INF/MANIFEST.MF, otherwise its
  // Export-Package directive would overwrite this one, leading to embedded dependencies not being exported.
  "mb.complex.tiger.eclipse",
  // Embedded packages to be exported. Using ';provider=mb;mandatory:=provider' to prevent these packages from being
  // imported with a regular Import-Package directive. They can only be used with a Require-Bundle dependency to this
  // bundle, or by qualifying an Import-Package directive with ';provider=mb'.
  "mb.complex.tiger.*;provider=mb;mandatory:=provider"
)
tasks {
  "jar"(Jar::class) {
    manifest {
      attributes(
        // Pass the above list as the Export-Package directive of the JAR manifest.
        Pair("Export-Package", exportPackage.joinToString(", "))
      )
    }
  }
}
