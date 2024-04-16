plugins {
  id("org.metaborg.gradle.config.root-project") version "0.5.6"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.5.6"
  id("org.metaborg.gradle.config.junit-testing") version "0.5.6"
  id("org.metaborg.gitonium") version "0.1.5"
  kotlin("jvm") version "1.3.41" // 1.3.41 in sync with kotlin-dsl plugin.
  `kotlin-dsl`
}

metaborg {
  kotlinApiVersion = "1.3"
  kotlinLanguageVersion = "1.3"
}

gradlePlugin {
  plugins {
    create("coronium-bundle-base") {
      id = "org.metaborg.coronium.bundle.base"
      implementationClass = "mb.coronium.plugin.base.BundleBasePlugin"
    }
    create("coronium-bundle") {
      id = "org.metaborg.coronium.bundle"
      implementationClass = "mb.coronium.plugin.BundlePlugin"
    }

    create("coronium-feature-base") {
      id = "org.metaborg.coronium.feature.base"
      implementationClass = "mb.coronium.plugin.base.FeatureBasePlugin"
    }
    create("coronium-feature") {
      id = "org.metaborg.coronium.feature"
      implementationClass = "mb.coronium.plugin.FeaturePlugin"
    }

    create("coronium-repository-base") {
      id = "org.metaborg.coronium.repository.base"
      implementationClass = "mb.coronium.plugin.base.RepositoryBasePlugin"
    }
    create("coronium-repository") {
      id = "org.metaborg.coronium.repository"
      implementationClass = "mb.coronium.plugin.RepositoryPlugin"
    }
  }
}

// Embed all dependencies into the plugin so that users do not receive the transitive dependency tree.
val embedded: Configuration = configurations.create("embedded")
configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(embedded)
val mavenResolverVersion = "1.3.3"
val mavenVersion = "3.6.0"
val bndGradleVersion = "5.0.1"
dependencies {
  embedded("org.apache.maven.resolver:maven-resolver-api:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-impl:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-connector-basic:$mavenResolverVersion")
  embedded("org.apache.maven.resolver:maven-resolver-transport-file:$mavenResolverVersion")
  embedded("org.apache.maven:maven-resolver-provider:$mavenVersion")
  embedded("org.apache.commons:commons-compress:1.18")
  embedded("biz.aQute.bnd:biz.aQute.bnd.gradle:$bndGradleVersion")
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
    // Allow duplicates, as some dependencies have duplicate files/classes.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
}
