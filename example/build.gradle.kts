plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.21"
  id("org.metaborg.gitonium") version "0.1.2"

  // Set versions for plugins to use, only applying them in subprojects (apply false here).
  id("org.metaborg.coronium.bundle") apply false // No version: use the plugin from the included composite build
  id("org.metaborg.coronium.feature") apply false
}

subprojects {
  metaborg {
    configureSubProject()
  }
}
