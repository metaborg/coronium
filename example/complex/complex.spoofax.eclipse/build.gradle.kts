plugins {
  id("org.metaborg.gradle.config.java-library")
  id("org.metaborg.coronium.bundle")
}

dependencies {
  api(platform(project(":complex.platform")))
  annotationProcessor(platform(project(":complex.platform")))

  bundleTargetPlatformApi(eclipse("javax.inject"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.runtime"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.expressions"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.resources"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.filesystem"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.views"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.editors"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.console"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.workbench"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.workbench.texteditor"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.ide"))
  bundleTargetPlatformApi(eclipse("org.eclipse.jface.text"))
  bundleTargetPlatformApi(eclipse("org.eclipse.swt"))
  bundleTargetPlatformApi(eclipse("com.ibm.icu"))

  bundleEmbedApi(project(":complex.spoofax"))
  bundleEmbedImplementation("org.metaborg:log.backend.slf4j")

  compileOnly("org.checkerframework:checker-qual-android")

  annotationProcessor("com.google.dagger:dagger-compiler")
}

val exports = listOf(
  "mb.complex.spoofax.*;provider=mb;mandatory:=provider",
  "mb.log.*;provider=mb;mandatory:=provider",
  "mb.pie.*;provider=mb;mandatory:=provider",
  "dagger.*;provider=mb;mandatory:=provider"
)
tasks {
  "jar"(Jar::class) {
    manifest {
      attributes(
        Pair("Export-Package", exports.joinToString(", "))
      )
    }
  }
}
