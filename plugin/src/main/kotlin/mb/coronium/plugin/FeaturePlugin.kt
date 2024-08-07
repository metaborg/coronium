@file:Suppress("UnstableApiUsage")

package mb.coronium.plugin

import mb.coronium.model.eclipse.BuildProperties
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.Feature
import mb.coronium.plugin.base.BundleBasePlugin
import mb.coronium.plugin.base.FeatureBasePlugin
import mb.coronium.plugin.base.bundleElements
import mb.coronium.plugin.base.bundleRuntimeClasspath
import mb.coronium.plugin.base.bundleUsage
import mb.coronium.plugin.base.featureElements
import mb.coronium.plugin.base.featureUsage
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.lazilyMavenize
import mb.coronium.task.EclipseRun
import mb.coronium.util.GradleLog
import mb.coronium.util.eclipseVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

open class FeatureExtension(project: Project) {
    val createPublication: Property<Boolean> = project.objects.property()

    init {
        createPublication.convention(true)
    }


    internal val log = GradleLog(project.logger)
}

@Suppress("unused")
class FeaturePlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory
) : Plugin<Project> {
    companion object {
        const val bundle = "bundle"
        const val featureInclude = "featureInclude"

        const val bundleReferences = "bundleReferences"
        const val featureIncludeReferences = "featureIncludeReferences"

        const val featureXmlFilename = "feature.xml"
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class)
        project.pluginManager.apply(JavaBasePlugin::class) // To apply several conventions to archive tasks.
        project.pluginManager.apply(MavenizePlugin::class)
        project.pluginManager.apply(BundleBasePlugin::class)
        project.pluginManager.apply(FeatureBasePlugin::class)

        val extension = FeatureExtension(project)
        project.extensions.add("feature", extension)

        configure(project, extension)
    }

    private fun configure(project: Project, extension: FeatureExtension) {
        configureConfigurations(project)
        val buildProperties = configureBuildProperties(project)
        val featureXmlOutputDir = project.buildDir.resolve("feature")
        val featureXmlOutputFile = featureXmlOutputDir.resolve(featureXmlFilename)
        val finalizeFeatureXmlTask = configureFinalizeFeatureXmlTask(project, extension, featureXmlOutputFile)
        configureFeatureJarTask(project, extension, buildProperties, featureXmlOutputDir, finalizeFeatureXmlTask)
        configureRunEclipseTask(project)
    }

    private fun configureConfigurations(project: Project) {
        // User-facing configurations
        val bundle = project.configurations.create(bundle) {
            description = "Dependencies to bundles to be included in this feature"
            isCanBeConsumed = false
            isCanBeResolved = false
            isVisible = false
        }
        val featureInclude = project.configurations.create(featureInclude) {
            description = "Dependencies to features to be included in this feature"
            isCanBeConsumed = false
            isCanBeResolved = false
            isVisible = false
        }
        // TODO: support feature requirement through a featureRequire configuration.

        // Internal (resolvable) configurations
        val bundleReferencesConfiguration = project.configurations.register(bundleReferences) {
            description = "References to bundles to include in the feature.xml"
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.bundleUsage)
            extendsFrom(bundle)
        }
        bundleReferencesConfiguration.configure { withDependencies { project.lazilyMavenize() } }
        val featureIncludeReferencesConfiguration = project.configurations.register(featureIncludeReferences) {
            description = "References to features to include in the feature.xml"
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.featureUsage)
            extendsFrom(featureInclude)
        }
        featureIncludeReferencesConfiguration.configure { withDependencies { project.lazilyMavenize() } }

        // Extend bundleElements and featureElements, such that the dependencies to included bundles and bundles from
        // included features are exported when deployed, which can then be consumed by other projects.
        project.bundleElements.extendsFrom(bundle, featureInclude)
        project.featureElements.extendsFrom(featureInclude)

        // Extend bundleRuntimeClasspath, such that included bundles and bundles from included features are loaded when
        // running Eclipse.
        project.bundleRuntimeClasspath.extendsFrom(bundle, featureInclude)
    }

    private fun configureBuildProperties(project: Project): BuildProperties {
        val builder = BuildProperties.Builder()
        val propertiesFile = project.file("build.properties").toPath()
        if (Files.isRegularFile(propertiesFile)) {
            builder.readFromPropertiesFile(propertiesFile)
        }
        // Remove 'feature.xml', since it is merged and included automatically.
        builder.binaryIncludes.removeIf {
            it == featureXmlFilename
        }
        return builder.build()
    }

    private fun configureFinalizeFeatureXmlTask(
        project: Project,
        extension: FeatureExtension,
        featureXmlOutputFile: File
    ): TaskProvider<*> {
        val featureXmlFile = project.file(featureXmlFilename).toPath()
        return project.tasks.register("finalizeFeatureXml") {
            // Depend on bundleReferences and featureIncludeReferences, because they influence how a feature model is built, which in turn influences the final feature XML file.
            dependsOn(project.bundleReferences)
            inputs.files({ project.bundleReferences }) // Closure to defer configuration resolution until task execution.
            dependsOn(project.featureIncludeReferences)
            inputs.files({ project.featureIncludeReferences }) // Closure to defer configuration resolution until task execution.

            //inputs.file(featureXmlFile).optional() // HACK: optional does not seem to work, just remove input for now?
            outputs.file(featureXmlOutputFile)

            doLast {
                // Build feature model from feature.xml and Gradle project.
                val feature = run {
                    val builder = Feature.Builder()
                    // Read feature model from feature.xml if it exists.
                    if (Files.isRegularFile(featureXmlFile)) {
                        builder.readFromFeatureXml(featureXmlFile, extension.log)
                    }

                    // Set name fom Gradle project if no name was set, or if the name does not match.
                    if (builder.id == null) {
                        builder.id = project.name
                    } else if (builder.id != project.name) {
                        extension.log.warning("Project name '${project.name}' differs from feature id '${builder.id}'; using '${project.name}' instead")
                        builder.id = project.name
                    }

                    // Set version from Gradle project if no version was set, or if the version does not match.
                    if (builder.version == null) {
                        builder.version = if (project.version == Project.DEFAULT_VERSION) {
                            extension.log.warning("Project '${project.name}' has no version set (i.e., version is '${Project.DEFAULT_VERSION}'), nor has a version been set in '$featureXmlFile', defaulting to version '${BundleVersion.zero()}'")
                            BundleVersion.zero()
                        } else {
                            project.eclipseVersion
                        }
                    } else if (builder.version != project.eclipseVersion) {
                        extension.log.warning("Eclipsified version '${project.eclipseVersion}' of project '${project.name}' differs from feature version '${builder.version}' in '$featureXmlFile'; using '${project.eclipseVersion}' instead")
                        builder.version = project.eclipseVersion
                    }

                    builder.build()
                }

                // Merge (add or replace version of) dependencies from bundleReferences and featureIncludeReferences into the feature.
                val mergedFeature = feature.mergeWith(
                    project.bundleReferences.resolvedConfiguration,
                    project.featureIncludeReferences.resolvedConfiguration
                )

                // Write the feature to a feature.xml file.
                featureXmlOutputFile.outputStream().buffered().use { outputStream ->
                    mergedFeature.writeToFeatureXml(outputStream)
                    outputStream.flush()
                }
            }
        }
    }

    private fun configureFeatureJarTask(
        project: Project,
        extension: FeatureExtension,
        buildProperties: BuildProperties,
        featureXmlOutputDir: File,
        finalizeFeatureXmlTask: TaskProvider<*>
    ) {
        val featureJarTask = project.tasks.register<Jar>("featureJar") {
            dependsOn(finalizeFeatureXmlTask)

            if (!buildProperties.binaryIncludes.isEmpty()) {
                from(project.projectDir) {
                    for (resource in buildProperties.binaryIncludes) {
                        include(resource)
                    }
                }
            }

            from(featureXmlOutputDir) {
                include(featureXmlFilename)
            }
        }
        project.tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME).dependsOn(featureJarTask)

        // Register the output of the Jar task as an artifact for the featureElements configuration.
        project.featureElements.outgoing.artifact(featureJarTask)

        // Create a new feature component and add variants from featureElements and bundleElements to it.
        val featureComponent = @Suppress("UnstableApiUsage") run {
            val featureComponent = softwareComponentFactory.adhoc("feature")
            project.components.add(featureComponent)
            featureComponent.addVariantsFromConfiguration(project.featureElements) {
                mapToMavenScope("runtime")
            }
            featureComponent.addVariantsFromConfiguration(project.bundleElements) {
                mapToMavenScope("runtime")
            }
            featureComponent
        }

        // Create a publication from the feature component.
        project.afterEvaluate {
            extension.createPublication.finalizeValue()
            if (extension.createPublication.get()) {
                project.pluginManager.withPlugin("maven-publish") {
                    project.extensions.configure<PublishingExtension> {
                        publications.register<MavenPublication>("Feature") {
                            from(featureComponent)
                        }
                    }
                }
            }
        }
    }

    private fun configureRunEclipseTask(project: Project) {
        project.tasks.register<EclipseRun>("runEclipse") {
            group = "coronium"
            description = "Runs this plugin in an Eclipse instance"

            val bundleRuntimeClasspath = project.bundleRuntimeClasspath

            // Depend on the bundle runtime configurations.
            dependsOn(bundleRuntimeClasspath)
            inputs.files({ bundleRuntimeClasspath }) // Closure to defer configuration resolution until task execution.

            doFirst {
                // At task execution time, before the run configuration is prepared, add all runtime dependencies as bundles.
                // This is done at task execution time because it resolves the configurations.
                for (file in bundleRuntimeClasspath) {
                    addBundle(file)
                }
                // Prepare run configuration before executing.
                prepareEclipseRunConfig()
            }
        }
    }
}

private val Project.bundleReferences get(): Configuration = this.configurations.getByName(FeaturePlugin.bundleReferences)
private val Project.featureIncludeReferences get(): Configuration = this.configurations.getByName(FeaturePlugin.featureIncludeReferences)
