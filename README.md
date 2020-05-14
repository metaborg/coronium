# Coronium

Coronium is a Gradle plugin for building, developing, and publishing Eclipse plugins.

## Status

### Supported

* __Eclipse plugins__: currently, Coronium supports building, developing (i.e, running an Eclipse instance with your plugin and its dependencies included), and publishing of Eclipse plugins to Maven repositories with Gradle metadata.

### Unsupported

* __Eclipse features and repositories__: not supported at the moment, but we want to support this in the short term.

* __P2 repositories__: not supported, and are unlikely to be supported on the short term, as Gradle does not support custom repository implementations.
Plugins can only be retrieved from Maven repositories that support Gradle metadata.
Consequently, Gradle metadata needs to be enabled for this to work, and the Maven repository needs to support Gradle metadata (basically every repository manager supports this).

* __Export/Import-Package__: not supported, un unlikely to ever be supported, as this is not really idiomatic in Gradle.

## Getting started

### Requirements

Gradle 5.3 or higher is required.
The following code snippets assume you are using Gradle with Kotlin, but should be translatable to Groovy as well.

### Applying the plugin

The Coronium plugin is not yet published to the Gradle plugins repository.
Therefore, to enable the plugin, add our repository to your settings.gradle(.kts) file:

```kotlin
pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/repositories/releases/")
  }
}
```

If you are on Gradle 5.3-5.6, Gradle metadata needs to be enabled. Add the following line to your settings.gradle(.kts) file:

```kotlin
enableFeaturePreview("GRADLE_METADATA")
```

Apply the bundle plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.bundle") version("0.3.0")
}
```

Now, your project will automatically be compiled into an OSGi bundle.
Eclipse plugins are just OSGi bundles with additional metadata, so we use the terms (OSGi) bundle and (Eclipse) plugin interchangeably.

### Making dependencies

The following configurations can be used to create dependencies to bundles, mimicking the `api`/`implementation` configurations of the `java-library` plugin:

* `bundleApi`/`bundleImplementation`: dependencies to bundles.
* `bundleTargetPlatformApi`/`bundleTargetPlatofrmImplementation`: dependencies to bundles in a target platform. Currently, only the `eclipse` target platform is supported, which points to a recent version of Eclipse.

Configurations ending with `Api` are transitive for both compiling and running dependents.
In OSGi terms, this becomes a `Require-Bundle` dependency with `;visibility:=reexport`.

Configurations ending with `Implementation` are not transitive.
In OSGi terms, this becomes a `Require-Bundle` dependency with `;visibility:=reexport`.
Coronium does handle this similarly to the `implementation` configuration of `java-plugin`, in that when you run your plugin, Coronium will include bundles that your plugin depends on in `Implementation` configurations.

For example, we can depend on several plugins of Eclipse as follows:

```kotlin
dependencies {
  bundleTargetPlatformApi(eclipse("javax.inject"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.runtime"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.expressions"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.resources"))
  bundleTargetPlatformApi(eclipse("org.eclipse.core.filesystem"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.views"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.editors"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.console"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.workbench"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.workbench.texteditor"))
  bundleTargetPlatformApi(eclipse("org.eclipse.ui.ide"))
  bundleTargetPlatformApi(eclipse("org.eclipse.jface.text"))
  bundleTargetPlatformApi(eclipse("org.eclipse.swt"))
  bundleTargetPlatformApi(eclipse("com.ibm.icu"))
}
```

We can also depend on bundles defined in other projects (e.g., through [multi-project](https://docs.gradle.org/current/userguide/multi_project_builds.html) or [composite builds](https://docs.gradle.org/current/userguide/composite_builds.html)), or those that are published to Maven repositories:

```kotlin
dependencies {
  bundleImplementation("org.metaborg:spoofax.eclipse:0.1.3")
  bundleApi(project(":tiger.eclipse"))
}
```

Besides these configurations, the following configurations from the `java-library` plugin are supported:

* `compileOnly`: for compile-only dependencies to Java libraries, usually for compile-time annotations.
* `annotationProcessor`: for annotation processor dependencies.

This enables us to use compile-time annotations and annotation processors in our plugin as usual. For example:

```kotlin
dependencies {
  compileOnly("org.checkerframework:checker-qual-android")
  compileOnly("org.immutables:value-annotations")
  compileOnly("org.derive4j:derive4j-annotation")

  annotationProcessor("org.immutables:value")
  annotationProcessor("org.derive4j:derive4j")
}
```

### Customizing `META-INF/MANIFEST.MF`

The manifest of your plugin can be customized by just having a `META-INF/MANIFEST.MF` file in your project, with your modifications.
Coronium will merge your manifest with the name, group, version, and dependencies of your Gradle project.
For example, we can create the following `META-INF/MANIFEST.MF` file to customize our plugin:

```manifest
Bundle-Activator: mb.spoofax.eclipse.SpoofaxPlugin
Export-Package: mb.spoofax.eclipse,
  mb.spoofax.eclipse.build,
  mb.spoofax.eclipse.command,
  mb.spoofax.eclipse.editor,
  mb.spoofax.eclipse.job,
  mb.spoofax.eclipse.log,
  mb.spoofax.eclipse.menu,
  mb.spoofax.eclipse.nature,
  mb.spoofax.eclipse.pie,
  mb.spoofax.eclipse.resource,
  mb.spoofax.eclipse.util
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
Bundle-ActivationPolicy: lazy
```

### Customizing `plugin.xml`

The `plugin.xml` file of your plugin can be customized by just having that file.
Coronium currently just copies it over.

### Embedding Java libraries

Coronium supports embedding Java libraries into your bundle with the `bundleEmbedApi`/`bundleEmbedImplementation` configurations.
This internally uses the [Gradle BND plugin](https://plugins.gradle.org/plugin/biz.aQute.bnd) to embed the libraries into the bundle.
To control what BND embeds and exports, either add entries to `Export-Package` in your `META-INF/MANIFEST.MF` file, or configure the BND plugin in Gradle.
For example, dependencies can be embedded as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.bundle") version("0.3.0")
}

dependencies {
  bundleTargetPlatformApi(eclipse("javax.inject"))

  bundleEmbedApi(project(":common"))
  bundleEmbedApi(project(":spoofax.core"))

  bundleEmbedApi("org.metaborg:log.api")
  bundleEmbedApi("org.metaborg:resource")
  bundleEmbedApi("org.metaborg:pie.api")
  bundleEmbedApi("org.metaborg:pie.runtime")
  bundleEmbedApi("org.metaborg:pie.dagger")

  bundleEmbedApi("org.metaborg:org.spoofax.terms")

  bundleEmbedApi("com.google.dagger:dagger")
}

val exports = listOf(
  "mb.*;provider=mb;mandatory:=provider",
  "org.spoofax.*;provider=mb;mandatory:=provider",
  "dagger;provider=mb;mandatory:=provider",
  "dagger.*;provider=mb;mandatory:=provider"
)
tasks {
  "jar"(Jar::class) {
    manifest {
      attributes(
        Pair("Export-Package", exports.joinToString(", "))
      )
    }
  }
}
```