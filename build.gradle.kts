plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.6"
  id("org.metaborg.gitonium") version "0.1.1"
  kotlin("jvm") version "1.3.21"
  `kotlin-dsl`
  `java-gradle-plugin`
  `maven-publish`
}

dependencies {
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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

tasks.withType<Test> {
  useJUnitPlatform {
    excludeTags.add("longRunning")
  }
}
