# Coronium
[![Build][github-build-badge]][github-build]
[![License][license-badge]][license]
[![GitHub Release][github-release-badge]][github-release]
[![Documentation][documentation-badge]][documentation]

Coronium is a Gradle plugin for building, developing, and publishing Eclipse plugins.


| Artifact                | Latest Release                                 |
|-------------------------|------------------------------------------------|
| `org.metaborg.coronium` | [![org.metaborg.coronium][maven-badge]][maven] |



## Status

### Supported

* __Eclipse plugins__: currently, Coronium supports building, developing (i.e, running an Eclipse instance with your plugin and its dependencies included), and publishing of Eclipse plugins to Maven repositories with Gradle metadata.

* __Eclipse features__: composite Eclipse features by creating dependencies to plugins and other features.

* __Eclipse repositories__: build Eclipse repositories from features.

* __Generate Eclipse installations__: generate Eclipse installations from repositories.

### Unsupported

* __P2 repositories__: not supported, and are unlikely to be supported on the short term, as Gradle does not support custom repository implementations.
Plugins can only be retrieved from Maven repositories that support Gradle metadata.
Consequently, Gradle metadata needs to be enabled for this to work, and the Maven repository needs to support Gradle metadata (basically every repository manager supports this).

* __Export/Import-Package__: not supported, un unlikely to ever be supported, as this is not really idiomatic in Gradle.

## Requirements

The required Java version depends on which version of Eclipse you are building against.
By default, you build against Eclipse 2020-06, which requires Java 8.
Eclipse 2020-09 and up require Java 11.

Compiling with higher Java version will most likely work.
However, running with higher Java versions may or may not work, depending on whether Eclipse runs on that Java version.

The code snippets in this README assume you are using Gradle with Kotlin, but should be translatable to Groovy as well.

## Prerequisites

The Coronium plugin is not yet published to the Gradle plugins repository.
Therefore, to enable downloading the plugin, add our repository to your settings.gradle(.kts) file:

```kotlin
pluginManagement {
  repositories {
    maven("https://artifacts.metaborg.org/content/repositories/releases/")
  }
}
```


## Building Eclipse plugins

### Applying the plugin

Apply the bundle plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.bundle") version("0.3.18")
}
```

The latest version of the plugin can be found at the top of this readme.

Now, your project will automatically be compiled into an OSGi bundle.
Eclipse plugins are just OSGi bundles with additional metadata, so we use the terms (OSGi) bundle and (Eclipse) plugin interchangeably.

### Making dependencies

The following configurations can be used to create dependencies to bundles, mimicking the `api`/`implementation` configurations of the `java-library` plugin:

* `bundleApi`/`bundleImplementation`: dependencies to bundles.
* `bundleTargetPlatformApi`/`bundleTargetPlatformImplementation`: dependencies to bundles in a target platform. Currently, only the `eclipse` target platform is supported, which points to a recent version of Eclipse.

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

To control what BND embeds and exports, either add entries to `Export-Package`/`Private-Package` in your `META-INF/MANIFEST.MF` file, or configure the BND plugin in Gradle.
Do not mix `Export-Package`/`Private-Package` directives between `META-INF/MANIFEST.MF` and the Gradle build file, as the contents of these directives are not merged and one will be chosen.

For example, dependencies can be embedded as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.bundle") version("0.3.18")
}

dependencies {
  bundleTargetPlatformApi(eclipse("javax.inject"))

  bundleEmbedApi(project(":complex.spoofax"))
  bundleEmbedApi("org.metaborg:log.api")
  bundleEmbedApi("org.metaborg:pie.api")
  bundleEmbedApi("com.google.dagger:dagger")

  bundleEmbedImplementation("org.metaborg:log.backend.slf4j")
  bundleEmbedImplementation("org.slf4j:slf4j-simple")
}

// For Java libraries that are embedded into the bundle, and are exported (i.e., bundleEmbedApi), we need to add an
// Export-Package directive to the JAR manifest that determines which packages should be exported. Only classes from
// these packages will be embedded. The BND plugin will perform the embedding. Therefore, the Export-Package
// syntax from BND is supported: https://bnd.bndtools.org/heads/export_package.html
val exportPackage = listOf(
  // Regular packages to be exported. Note that this export cannot be written in META-INF/MANIFEST.MF, otherwise its
  // Export-Package directive would overwrite this one, leading to embedded dependencies not being exported.
  "mb.spoofax.eclipse",
  // Embedded packages to be exported. Using ';provider=mb;mandatory:=provider' to prevent these packages from being
  // imported with a regular Import-Package directive. They can only be used with a Require-Bundle dependency to this
  // bundle, or by qualifying an Import-Package directive with ';provider=mb'.
  "mb.spoofax.*;provider=mb;mandatory:=provider",
  "mb.log.api.*;provider=mb;mandatory:=provider",
  "mb.pie.*;provider=mb;mandatory:=provider",
  "dagger.*;provider=mb;mandatory:=provider"
)
// Likewise, for Java libraries that are embedded into the bundle, but not exported (i.e., bundleEmbedImplementation),
// we need to add a Private-Package directive to the JAR manifest that determines which packages should be included.
// Only classes from these packages will be embedded. Again, the BND plugin will perform the embedding. Therefore, the
// Private-Package syntax from BND is supported: https://bnd.bndtools.org/heads/private_package.html
val privatePackage = listOf(
  "mb.log.slf4j.*",
  "org.slf4j.*"
)
tasks {
  "jar"(Jar::class) {
    manifest {
      attributes(
        // Pass the above lists as the Export-Package and Private-Package directives of the JAR manifest.
        Pair("Export-Package", exportPackage.joinToString(", ")),
        Pair("Private-Package", privatePackage.joinToString(", "))
      )
    }
  }
}
```

## Building Eclipse features

Apply the feature plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.feature") version("0.3.18")
}
```

Include bundles and/or other in the feature by making dependencies:

```kotlin
dependencies {
  bundle(project(":tiger.eclipse"))

  featureInclude(project(":spoofax.eclipse.feature"))
}
```

By default, dependencies are transitive. You can use the regular exclude mechanisms in Gradle to exclude transitive dependencies. For example:

```kotlin
dependencies {
  featureInclude(project(":spoofax.eclipse.feature"))
  bundle(project(":tiger.eclipse")) {
    // Including a bundle into a feature also includes all reexported bundles. In this case, we want to prevent this
    // because 'complex.spoofax.eclipse' is included into the 'complex.spoofax.eclipse.feature' feature.
    exclude("org.metaborg", "spoofax.eclipse")
  }
}
```

## Building Eclipse repositories

Apply the repository plugin to a project (a build.gradle(.kts) file) as follows:

```kotlin
plugins {
  id("org.metaborg.coronium.repository") version("0.3.18")
}
```

Include features into the repository by making dependencies:

```kotlin
dependencies {
  feature(project(":tiger.eclipse.feature"))
}
```

## Running the Eclipse IDE with plugins/features/repositories

To start an Eclipse IDE instance with your plugin, feature, or repository included in the IDE, simply execute the `runEclipse` task.
Currently, this will start [Eclipse IDE for Eclipse Committers 2020-06](https://www.eclipse.org/downloads/packages/release/2020-06/r/eclipse-ide-eclipse-committers).
This variant and version is currently hardcoded, but will be made configurable in the future.

When `runEclipse` is used on a feature, all plugins that are (transitively) included in the feature will be loaded into the Eclipse instance.
Likewise, when used on a repository, all plugins that are (transitively) included in the repository and included from features will be loaded into the Eclipse instance.


## Generating Eclipse installations

Eclipse installations can be generated from repositories.
On an Eclipse repository project, run the `createEclipseInstallation` task to generate an Eclipse installation that includes the features and bundles of the repository.
The generated Eclipse installation will be located in the `build/eclipse-<os>-<arch>` directory.
Running `archiveEclipseInstallation` will additionally create an archive of the installation at `build/dist/Eclipse-<os>-<arch>.zip`.
Finally, running `createEclipseInstallationWithJvm` and/or `archiveEclipseInstallationWithJvm` will create/archive an Eclipse installation with an embedded JVM, so that no JVM needs to be installed on the system to run that Eclipse installation.
These are located at `build/eclipse-<os>-<arch>-jvm` and `build/dist/Eclipse-<os>-<arch>-jvm.zip`

To generate Eclipse installations for all operating system and architecture combinations for distribution purposes, run the `archiveEclipseInstallations` and `archiveEclipseInstallationsWithJvm` tasks.

The name of the installation can be changed with the `eclipseInstallationAppName` property, and additional Eclipse repositories and units to install can be provided with the `eclipseInstallationAdditionalRepositories` and `eclipseInstallationAdditionalInstallUnits` properties. For example:

```kotlin
repository {
  eclipseInstallationAppName.set("Tiger")
  eclipseInstallationAdditionalRepositories.add("https://de-jcup.github.io/update-site-eclipse-yaml-editor/update-site/")
  eclipseInstallationAdditionalInstallUnits.add("de.jcup.yamleditor.feature.group")
}
```

Currently, these tasks are hardcoded to generate Eclipse 2022-06 for Java developers instances, and JVMs are hardcoded to Eclipse Temurin 11.0.22+7 JDKs with HotSpot.
This will be made configurable in the future.

## Publishing

Bundles, features, and repositories can all be published via the [standard Gradle `maven-publish` plugin](https://docs.gradle.org/current/userguide/publishing_maven.html).
Bundles are published as regular Java libraries with additional metadata. See the [Setting up basic publishing](https://docs.gradle.org/current/userguide/publishing_setup.html#sec:basic_publishing) guide to configure publications.
For example, to publish a Bundle:

```kotlin
plugins {
  id("org.metaborg.coronium.bundle") version("0.3.18")
  `maven-publish`
}

publishing {
  publications {
    create<MavenPublication>("myBundle") {
      from(components["java"])
    }
  }
}
```

For features and repositories, Coronium will automatically configure *what* to publish.
The only thing that needs to be done is to enable the `maven-publish` plugin.
For features:

```kotlin
plugins {
  id("org.metaborg.coronium.feature") version("0.3.18")
  `maven-publish`
}
```

For repositories:

```kotlin
plugins {
  id("org.metaborg.coronium.repository") version("0.3.18")
  `maven-publish`
}
```

It is up to you to configure [*where* to publish to](https://docs.gradle.org/current/userguide/publishing_setup.html#publishing_overview:where).

If you would like to disable generating publications, set the `createPublication` property to `false` in the corresponding extension.
For features:

```kotlin
feature {
  createPublication.set(false)
}
```

For repositories:

```kotlin
repository {
  createPublication.set(false)
}
```

Finally, it is possible to automatically publish the Eclipse installations generated from a repository by setting the `createEclipseInstallationPublications` and/or `createEclipseInstallationWithJvmPublications` property to `true`:

```kotlin
repository {
  createEclipseInstallationPublications.set(true)
  createEclipseInstallationWithJvmPublications.set(true)
}
```

## Development

This section details the development of this project.

### Building

This repository is built with Gradle, which requires a JDK of at least version 8 to be installed. Higher versions may work depending on [which version of Gradle is used](https://docs.gradle.org/current/userguide/compatibility.html).

To build this repository, run `./gradlew buildAll` on Linux and macOS, or `gradlew buildAll` on Windows.

### Automated Builds

All branches and tags of this repository are built on:
- [GitHub actions](https://github.com/metaborg/coronium/actions/workflows/build.yml) via `.github/workflows/build.yml`.
- Our [Jenkins buildfarm](https://buildfarm.metaborg.org/view/Devenv/job/metaborg/job/coronium/) via `Jenkinsfile` which uses our [Jenkins pipeline library](https://github.com/metaborg/jenkins.pipeline/).

### Publishing

This repository is published via Gradle and Git with the [Gitonium](https://github.com/metaborg/gitonium) and [Gradle Config](https://github.com/metaborg/gradle.config) plugins.
It is published to our [artifact server](https://artifacts.metaborg.org) in the [releases repository](https://artifacts.metaborg.org/content/repositories/releases/).

First update `CHANGELOG.md` with your changes, create a new release entry, and update the release links at the bottom of the file.
Then, commit your changes.

To make a new release, create a tag in the form of `release-*` where `*` is the version of the release you'd like to make.
Then first build the project with `./gradlew buildAll` to check if building succeeds.

If you want our buildfarm to publish this release, just push the tag you just made, and our buildfarm will build the repository and publish the release.

If you want to publish this release locally, you will need an account with write access to our artifact server, and tell Gradle about this account.
Create the `./gradle/gradle.properties` file if it does not exist.
Add the following lines to it, replacing `<username>` and `<password>` with those of your artifact server account:
```
publish.repository.metaborg.artifacts.username=<username>
publish.repository.metaborg.artifacts.password=<password>
```
Then run `./gradlew publishAll` to publish all built artifacts.
You should also push the release tag you made such that this release is reproducible by others.


## License
Copyright 2018-2024 Delft University of Technology

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at <https://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an **"as is" basis, without warranties or conditions of any kind**, either express or implied. See the License for the specific language governing permissions and limitations under the License.


[github-build-badge]: https://img.shields.io/github/actions/workflow/status/metaborg/coronium/build.yaml
[github-build]: https://github.com/metaborg/coronium/actions
[license-badge]: https://img.shields.io/github/license/metaborg/coronium
[license]: https://github.com/metaborg/coronium/blob/master/LICENSE
[github-release-badge]: https://img.shields.io/github/v/release/metaborg/coronium
[github-release]: https://github.com/metaborg/coronium/releases

[maven-badge]: https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fartifacts.metaborg.org%2Fcontent%2Frepositories%2Freleases%2Forg%2Fmetaborg%2Fcoronium%2Fmaven-metadata.xml
[maven]: https://artifacts.metaborg.org/#nexus-search;gav~org.metaborg~coronium~~~
