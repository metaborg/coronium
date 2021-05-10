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

dependencies {
  feature(project(":complex.tiger.eclipse.feature"))
}
