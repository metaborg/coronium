plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.21"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.3.21"
  id("org.metaborg.gradle.config.junit-testing") version "0.3.21"
  id("org.metaborg.gitonium") version "0.1.2"
  kotlin("jvm") version "1.3.61"
  `kotlin-dsl`
}

metaborg {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
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

// Embed all dependencies into the plugin so that users do not receive the transitive dependency tree.
val embedded: Configuration = configurations.create("embedded")
configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(embedded)
val mavenResolverVersion = "1.3.3"
val mavenVersion = "3.6.0"
dependencies {
  embedded("org.apache.maven.resolver:maven-resolver-api:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-impl:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-connector-basic:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-transport-file:$mavenResolverVersion")
  embedded("org.apache.maven:maven-resolver-provider:$mavenVersion")
  embedded("org.apache.commons:commons-compress:1.18")
}
tasks {
  jar {
    // Closure inside from to defer evaluation of configuration until task execution time.
    from({ embedded.filter { it.exists() }.map { if(it.isDirectory) it else zipTree(it) } }) {
      // Exclude signature files from dependencies, otherwise the JVM will refuse to load the created JAR file.
      exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    // Enable zip64 to support ZIP files with more than 2^16 entries, which we need.
    isZip64 = true
  }
}
