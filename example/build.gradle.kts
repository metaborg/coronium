plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.6"
  id("org.metaborg.gitonium") version "0.1.5"

  // Set versions for plugins to use, only applying them in subprojects (apply false here).
  id("org.metaborg.coronium.bundle") apply false // No version: use the plugin from the included composite build
  id("org.metaborg.coronium.feature") apply false
}

subprojects {
  metaborg {
    configureSubProject()
  }
}

allprojects {
  // Disable actual publishing tasks to prevent this repository from being actually published.
  tasks.all {
    if(name.contains("publish") && !name.contains("ToMavenLocal")) {
      enabled = false
    }
  }
}
