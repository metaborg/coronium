import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  // Stick with version 1.3.10 because the kotlin-dsl plugin uses that.
  kotlin("jvm") version "1.3.10"
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
  id("org.metaborg.gitonium") version "0.3.0"
}

group = "org.metaborg"

repositories {
  maven(url = "http://home.gohla.nl:8091/artifactory/all/")
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

publishing {
  repositories {
    maven {
      name = "Artifactory"
      url = uri("http://home.gohla.nl:8091/artifactory/all/")
      credentials {
        username = project.findProperty("publish.repository.Artifactory.username")?.toString()
        password = project.findProperty("publish.repository.Artifactory.password")?.toString()
      }
    }
  }
}
