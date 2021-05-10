plugins {
  id("org.metaborg.coronium.repository")
  `maven-publish`
}

repository {
  eclipseInstallationAppName.set("Tiger")
  createEclipseInstallationPublications.set(true)
  createEclipseInstallationWithJvmPublications.set(true)
}

dependencies {
  feature(project(":complex.tiger.eclipse.feature"))
}

// Disable actual publishing tasks to prevent this repository from being actually published.
tasks.all {
  if(name.contains("publish") && !name.contains("ToMavenLocal")) {
    enabled = false
  }
}
