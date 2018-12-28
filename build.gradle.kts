import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// HACK: load our plugin via buildscript classpath and apply to work around IntelliJ bug which prevents custom plugins in composite builds.
buildscript {
  repositories {
    flatDir { dirs("../gitonium/build/libs") }
    mavenCentral()
    jcenter()
  }
  dependencies {
    classpath("org.metaborg", "gitonium", "master-SNAPSHOT")
    classpath("org.eclipse.jgit:org.eclipse.jgit:5.2.0.201812061821-r")
  }
}
apply {
  plugin("org.metaborg.gitonium")
}

plugins {
  // Stick with version 1.3.10 because the kotlin-dsl plugin uses that.
  kotlin("jvm") version "1.3.10" apply true
  `kotlin-dsl`
  `java-gradle-plugin`
}

group = "org.metaborg"

repositories {
  mavenCentral()
  jcenter()
}

dependencies {
  compile(kotlin("stdlib"))

  compile("org.apache.maven.resolver:maven-resolver-api:1.1.1")
  compile("org.apache.maven.resolver:maven-resolver-impl:1.1.1")
  compile("org.apache.maven.resolver:maven-resolver-connector-basic:1.1.1")
  compile("org.apache.maven.resolver:maven-resolver-transport-file:1.1.1")
  compile("org.apache.maven:maven-resolver-provider:3.5.4")
  compile("org.apache.commons:commons-compress:1.18")

  testCompile("org.junit.jupiter:junit-jupiter-api:5.3.1")
  testRuntime("org.junit.jupiter:junit-jupiter-engine:5.3.1")
}

kotlinDslPluginOptions {
  experimentalWarning.set(false)
}
tasks.withType<KotlinCompile>().all {
  kotlinOptions.jvmTarget = "1.8"
}

gradlePlugin {
  plugins {
    create("coronium-bundle") {
      id = "org.metaborg.coronium.bundle"
      implementationClass = "mb.coronium.plugin.BundlePlugin"
    }
    create("coronium-feature") {
      id = "org.metaborg.coronium.feature"
      implementationClass = "mb.coronium.plugin.FeaturePlugin"
    }
    create("coronium-repository") {
      id = "org.metaborg.coronium.repository"
      implementationClass = "mb.coronium.plugin.RepositoryPlugin"
    }
    create("coronium-embedding") {
      id = "org.metaborg.coronium.embedding"
      implementationClass = "mb.coronium.plugin.EmbeddingPlugin"
    }
  }
}

tasks.withType<Test> {
  useJUnitPlatform {
    excludeTags.add("longRunning")
  }
}

tasks {
  register("buildAll") {
    dependsOn("build")
  }
  register("cleanAll") {
    dependsOn("clean")
  }
}
