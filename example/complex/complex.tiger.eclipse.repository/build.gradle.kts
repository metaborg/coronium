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
