rootProject.name = "coronium.example"

pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/groups/public/")
  }
}

if(org.gradle.util.VersionNumber.parse(gradle.gradleVersion).major < 6) {
  enableFeaturePreview("GRADLE_METADATA")
}

// Only include composite builds when this is the root project (it has no parent), for example when running Gradle tasks
// from the command-line. Otherwise, the parent project will include these composite builds.
if(gradle.parent == null) {
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
