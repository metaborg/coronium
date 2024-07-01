@file:Suppress("UnstableApiUsage")

package mb.coronium.plugin

import mb.coronium.model.eclipse.Bundle
import mb.coronium.model.eclipse.BundleVersion
import mb.coronium.model.eclipse.BundleVersionRange
import mb.coronium.model.eclipse.Feature
import mb.coronium.model.eclipse.Repository
import mb.coronium.plugin.base.BundleBasePlugin
import mb.coronium.plugin.base.FeatureBasePlugin
import mb.coronium.plugin.base.RepositoryBasePlugin
import mb.coronium.plugin.base.bundleRuntimeClasspath
import mb.coronium.plugin.base.bundleUsage
import mb.coronium.plugin.base.featureUsage
import mb.coronium.plugin.base.repositoryArchive
import mb.coronium.plugin.internal.MavenizePlugin
import mb.coronium.plugin.internal.lazilyMavenize
import mb.coronium.task.ArchiveEclipseInstallation
import mb.coronium.task.EclipseCreateInstallation
import mb.coronium.task.EclipseInstallationAddJvm
import mb.coronium.task.EclipseRun
import mb.coronium.util.Arch
import mb.coronium.util.GradleLog
import mb.coronium.util.Os
import mb.coronium.util.TempDir
import mb.coronium.util.packJar
import mb.coronium.util.readManifestFromFile
import mb.coronium.util.unarchive
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

open class RepositoryExtension(project: Project) {
    val repositoryDescriptionFile: Property<String> = project.objects.property()
    val qualifierReplacement: Property<String> = project.objects.property()

    val eclipseInstallationAppName: Property<String> = project.objects.property()
    val eclipseInstallationAdditionalRepositories: ListProperty<String> = project.objects.listProperty()
    val eclipseInstallationAdditionalInstallUnits: ListProperty<String> = project.objects.listProperty()

    val createPublication: Property<Boolean> = project.objects.property()
    val createEclipseInstallationPublications: Property<Boolean> = project.objects.property()
    val createEclipseInstallationWithJvmPublications: Property<Boolean> = project.objects.property()
    val eclipseInstallationPublicationClassifierPrefix: Property<String> = project.objects.property()

    init {
        repositoryDescriptionFile.convention("category.xml")
        qualifierReplacement.convention(SimpleDateFormat("yyyyMMddHHmm").format(Calendar.getInstance().time))

        eclipseInstallationAppName.convention("Eclipse")

        createPublication.convention(true)
        createEclipseInstallationPublications.convention(false)
        createEclipseInstallationWithJvmPublications.convention(false)
        eclipseInstallationPublicationClassifierPrefix.convention(eclipseInstallationAppName.map { it.toLowerCase() })
    }


    internal val log = GradleLog(project.logger)
}

@Suppress("unused")
class RepositoryPlugin @Inject constructor(
    private val softwareComponentFactory: SoftwareComponentFactory
) : Plugin<Project> {
    companion object {
        const val feature = "feature"
        const val repositoryFeatureArtifacts = "repositoryFeatureArtifacts"
        const val repositoryBundleArtifacts = "repositoryBundleArtifacts"
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class)
        project.pluginManager.apply(JavaBasePlugin::class) // To apply several conventions to archive tasks.
        project.pluginManager.apply(MavenizePlugin::class)
        project.pluginManager.apply(BundleBasePlugin::class)
        project.pluginManager.apply(FeatureBasePlugin::class)
        project.pluginManager.apply(RepositoryBasePlugin::class)

        val extension = RepositoryExtension(project)
        project.extensions.add("repository", extension)

        configure(project, extension)
    }

    private fun configure(project: Project, extension: RepositoryExtension) {
        configureConfigurations(project)

        val repositorySource = project.buildDir.resolve("repositorySource")

        val copiedBundlesDir = repositorySource.resolve("bundles")
        val copyBundlesTask = configureCopyBundlesTask(project, copiedBundlesDir)

        val copiedFeaturesDir = repositorySource.resolve("features")
        val copyFeaturesTask = configureCopyFeaturesTask(project, copiedFeaturesDir)

        val replacedQualifierDir = project.buildDir.resolve("replacedQualifier")
        val replaceQualifierTask = configureReplaceQualifierTask(
            project,
            extension,
            copyBundlesTask,
            copyFeaturesTask,
            copiedBundlesDir,
            copiedFeaturesDir,
            replacedQualifierDir
        )

        val repositoryXmlFile = project.buildDir.resolve("repositoryXml/repository.xml")
        val createRepositoryXmlTask = configureCreateRepositoryXmlTask(project, extension, repositoryXmlFile)

        val repositoryDir = project.buildDir.resolve("repository")
        val createRepositoryTask = configureCreateRepositoryTask(
            project,
            replaceQualifierTask,
            createRepositoryXmlTask,
            replacedQualifierDir,
            repositoryXmlFile,
            repositoryDir
        )

        configurePublishingTasks(project, extension, createRepositoryTask, repositoryDir)
        configureRunEclipseTask(project)
    }

    private fun configureConfigurations(project: Project) {
        // User-facing configurations
        val feature = project.configurations.create(feature) {
            description = "Feature dependencies to be included in the repository"
            isCanBeConsumed = false
            isCanBeResolved = false
            isVisible = false
        }

        // Internal (resolvable) configurations
        val repositoryBundleArtifactsConfiguration = project.configurations.register(repositoryBundleArtifacts) {
            description = "Bundle artifacts that should end up in the repository"
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.bundleUsage)
            extendsFrom(feature)
        }
        repositoryBundleArtifactsConfiguration.configure { withDependencies { project.lazilyMavenize() } }
        val repositoryFeatureArtifactsConfiguration = project.configurations.register(repositoryFeatureArtifacts) {
            description = "Feature artifacts that should end up in the repository"
            isCanBeConsumed = false
            isCanBeResolved = true
            isVisible = false
            attributes.attribute(Usage.USAGE_ATTRIBUTE, project.featureUsage)
            extendsFrom(feature)
        }
        repositoryFeatureArtifactsConfiguration.configure { withDependencies { project.lazilyMavenize() } }

        // Extend bundleRuntimeClasspath, such that bundles included from features are loaded when running Eclipse.
        project.bundleRuntimeClasspath.extendsFrom(feature)
    }

    private fun configureCopyBundlesTask(project: Project, copiedBundlesDir: File): TaskProvider<*> {
        val repositoryBundleArtifacts = project.repositoryBundleArtifacts
        return project.tasks.register<Sync>("copyBundles") {
            dependsOn(repositoryBundleArtifacts)
            inputs.files({ repositoryBundleArtifacts })

            from({ repositoryBundleArtifacts })
            into(copiedBundlesDir)

            doFirst {
                copiedBundlesDir.mkdirs()
            }
        }
    }

    private fun configureCopyFeaturesTask(project: Project, copiedFeaturesDir: File): TaskProvider<*> {
        val repositoryFeatureArtifacts = project.repositoryFeatureArtifacts
        return project.tasks.register<Sync>("copyFeatures") {
            dependsOn(repositoryFeatureArtifacts)
            inputs.files({ repositoryFeatureArtifacts })

            from({ repositoryFeatureArtifacts })
            into(copiedFeaturesDir)

            doFirst {
                copiedFeaturesDir.mkdirs()
            }
        }
    }

    private fun configureReplaceQualifierTask(
        project: Project,
        extension: RepositoryExtension,
        copyBundlesTask: TaskProvider<*>,
        copyFeaturesTask: TaskProvider<*>,
        copiedBundlesDir: File,
        copiedFeaturesDir: File,
        replacedQualifierDir: File
    ): TaskProvider<*> {
        // Replace '.qualifier' with concrete qualifiers in all features and plugins. Have to do the unpacking/packing of
        // JAR files manually, as we cannot create Gradle tasks during execution.
        val featuresInReplaceQualifierDir = replacedQualifierDir.resolve("features").toPath()
        val pluginsInReplaceQualifierDir = replacedQualifierDir.resolve("plugins").toPath()
        val log = extension.log
        return project.tasks.register("replaceQualifier") {
            dependsOn(copyBundlesTask, copyFeaturesTask)

            inputs.dir(copiedBundlesDir)
            inputs.dir(copiedFeaturesDir)

            outputs.dir(replacedQualifierDir)

            doFirst {
                // Delete replaced qualifier dir to ensure that removed bundles/features are not included.
                replacedQualifierDir.deleteRecursively()
                Files.createDirectories(featuresInReplaceQualifierDir)
                Files.createDirectories(pluginsInReplaceQualifierDir)
            }

            doLast {
                extension.qualifierReplacement.finalizeValue()
                val concreteQualifier = extension.qualifierReplacement.get()
                TempDir("replaceQualifier").use { tempDir ->
                    Files.list(copiedFeaturesDir.toPath()).use { featureJarFiles ->
                        for (featureJarFile in featureJarFiles) {
                            val fileName = featureJarFile.fileName
                            val unpackTempDir = tempDir.createTempDir(fileName.toString())
                            unarchive(featureJarFile, unpackTempDir, log)
                            val featureXmlFile = unpackTempDir.resolve("feature.xml")
                            if (Files.isRegularFile(featureXmlFile)) {
                                val builder = Feature.Builder()
                                builder.readFromFeatureXml(featureXmlFile, log)
                                // Replace qualifier in version.
                                builder.version = builder.version?.mapQualifier {
                                    it?.replace("qualifier", concreteQualifier)
                                }
                                // Replace qualifier in dependencies.
                                builder.bundleIncludes = builder.bundleIncludes.map { dep ->
                                    dep.mapVersion { version ->
                                        version.mapQualifier {
                                            it?.replace("qualifier", concreteQualifier)
                                        }
                                    }
                                }.toMutableList()
                                builder.featureIncludes = builder.featureIncludes.map { dep ->
                                    dep.mapVersion { version ->
                                        version.mapQualifier {
                                            it?.replace("qualifier", concreteQualifier)
                                        }
                                    }
                                }.toMutableList()
                                // Write back feature XML.
                                val feature = builder.build()
                                Files.newOutputStream(featureXmlFile).buffered().use { outputStream ->
                                    feature.writeToFeatureXml(outputStream)
                                    outputStream.flush()
                                }
                            } else {
                                log.warning("Unable to replace qualifiers in versions for $fileName, as it has no feature.xml file")
                            }
                            val targetJarFile = featuresInReplaceQualifierDir.resolve(fileName)
                            packJar(unpackTempDir, targetJarFile)
                        }
                    }
                    Files.list(copiedBundlesDir.toPath()).use { pluginJarFiles ->
                        for (pluginJarFile in pluginJarFiles) {
                            val fileName = pluginJarFile.fileName
                            val unpackTempDir = tempDir.createTempDir(fileName.toString())
                            unarchive(pluginJarFile, unpackTempDir, log)
                            val manifestFile = unpackTempDir.resolve("META-INF/MANIFEST.MF")
                            if (Files.isRegularFile(manifestFile)) {
                                val manifest = readManifestFromFile(manifestFile)
                                val builder = Bundle.Builder()
                                builder.readFromManifestAttributes(manifest.mainAttributes, log)
                                // Replace qualifier in version.
                                builder.coordinates.version = builder.coordinates.version?.mapQualifier {
                                    it?.replace("qualifier", concreteQualifier)
                                }
                                // Replace qualifier in dependencies.
                                builder.requiredBundles = builder.requiredBundles.map { dep ->
                                    dep.mapVersion { version ->
                                        when (version) {
                                            is BundleVersion -> version.mapQualifier {
                                                it?.replace(
                                                    "qualifier",
                                                    concreteQualifier
                                                )
                                            }

                                            is BundleVersionRange -> version.mapQualifiers {
                                                it?.replace(
                                                    "qualifier",
                                                    concreteQualifier
                                                )
                                            }

                                            null -> null
                                        }
                                    }
                                }.toMutableList()
                                // Write back to manifest attributes.
                                val bundle = builder.build()
                                val attributesMap = bundle.writeToManifestAttributes()
                                for ((name, value) in attributesMap) {
                                    manifest.mainAttributes.putValue(name, value)
                                }
                                // Write manifest back to file.
                                Files.newOutputStream(manifestFile).buffered().use { outputStream ->
                                    manifest.write(outputStream)
                                    outputStream.flush()
                                }
                            } else {
                                log.warning("Unable to replace qualifiers in versions for $fileName, as it has no META-INF/MANIFEST.MF file")
                            }
                            val targetJarFile = pluginsInReplaceQualifierDir.resolve(fileName)
                            packJar(unpackTempDir, targetJarFile)
                        }
                    }
                }
            }
        }
    }

    private fun configureCreateRepositoryXmlTask(
        project: Project,
        extension: RepositoryExtension,
        repositoryXmlFile: File
    ): TaskProvider<*> {
        val repositoryFeatureArtifacts = project.repositoryFeatureArtifacts
        return project.tasks.register("createRepositoryXml") {
            dependsOn(repositoryFeatureArtifacts)
            inputs.files({ repositoryFeatureArtifacts })
            outputs.file(repositoryXmlFile)
            doLast {
                // Build repository model from repository description file (category.xml or site.xml) and Gradle project.
                val repository = run {
                    val builder = Repository.Builder()
                    val repositoryDescriptionFile = project.file(extension.repositoryDescriptionFile).toPath()
                    if (Files.isRegularFile(repositoryDescriptionFile)) {
                        builder.readFromRepositoryXml(repositoryDescriptionFile, extension.log)
                    }
                    builder.updateVersionsFrom(repositoryFeatureArtifacts.resolvedConfiguration)
                    val repository = builder.build()
                    repository.mergeWith(repositoryFeatureArtifacts.resolvedConfiguration)
                }
                // Write repository model to file.
                repositoryXmlFile.parentFile.mkdirs()
                repositoryXmlFile.outputStream().buffered().use { outputStream ->
                    repository.writeToRepositoryXml(outputStream)
                    outputStream.flush()
                }
            }
        }
    }

    private fun configureCreateRepositoryTask(
        project: Project,
        replaceQualifierTask: TaskProvider<*>,
        createRepositoryXmlTask: TaskProvider<*>,
        replacedQualifierDir: File,
        repositoryXmlFile: File,
        repositoryDir: File
    ): TaskProvider<*> {
        return project.tasks.register("createRepository") {
            dependsOn(replaceQualifierTask)
            dependsOn(createRepositoryXmlTask)
            inputs.dir(replacedQualifierDir)
            inputs.file(repositoryXmlFile)
            outputs.dir(repositoryDir)
            doLast {
                val eclipseLauncherPath = project.lazilyMavenize().equinoxLauncherPath()?.toString()
                    ?: error("Could not find Eclipse launcher")

                repositoryDir.deleteRecursively()
                repositoryDir.mkdirs()
                project.javaexec {
                    classpath = project.files(eclipseLauncherPath)
                    args = mutableListOf(
                        "-application", "org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher",
                        "-metadataRepository", "file:/$repositoryDir",
                        "-artifactRepository", "file:/$repositoryDir",
                        "-source", "$replacedQualifierDir",
                        "-configs", "ANY",
                        "-compress",
                        "-publishArtifacts"
                    )
                }
                project.javaexec {
                    classpath = project.files(eclipseLauncherPath)
                    args = mutableListOf(
                        "-application", "org.eclipse.equinox.p2.publisher.CategoryPublisher",
                        "-metadataRepository", "file:/$repositoryDir",
                        "-categoryDefinition", "file:/$repositoryXmlFile",
                        "-categoryQualifier",
                        "-compress"
                    )
                }
            }
        }
    }

    private fun configurePublishingTasks(
        project: Project,
        extension: RepositoryExtension,
        createRepositoryTask: TaskProvider<*>,
        repositoryDir: File
    ) {
        // Register task that archives the repository.
        val archiveRepositoryTask = project.tasks.register<Zip>("archiveRepository") {
            dependsOn(createRepositoryTask)
            from(repositoryDir)
        }
        project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(archiveRepositoryTask)
        // Register the output of the archive task as an artifact for the repositoryArchive configuration.
        project.repositoryArchive.outgoing.artifact(archiveRepositoryTask)
        // Create a new repository component and add variants from the repositoryArchive to it.
        val repositoryComponent = @Suppress("UnstableApiUsage") run {
            val component = softwareComponentFactory.adhoc("repository")
            project.components.add(component)
            component.addVariantsFromConfiguration(project.repositoryArchive) {
                mapToMavenScope("runtime")
            }
            component
        }

        // Register a task for creating and archiving (with and without embedded JRE) for every OS/architecture combination.
        data class OsArch(val os: Os, val arch: Arch)

        val createTaskPerOsArch = mutableMapOf<OsArch, TaskProvider<*>>()
        val archiveTaskPerOsArch = mutableMapOf<OsArch, TaskProvider<*>>()
        val createWithJvmTaskPerOsArch = mutableMapOf<OsArch, TaskProvider<*>>()
        val archiveWithJvmTaskPerOsArch = mutableMapOf<OsArch, TaskProvider<*>>()
        for (os in Os.values()) {
            for (arch in os.validArchs) {
                val osArch = OsArch(os, arch)
                val taskSuffix = "${os.displayName}${arch.displayName}"

                val createTask =
                    project.tasks.register<EclipseCreateInstallation>("createEclipseInstallationFor$taskSuffix") {
                        group = "coronium"
                        description =
                            "Create an Eclipse installation for ${os.displayName} ${arch.displayName}, including the features of this repository"

                        dependsOn(createRepositoryTask)
                        repositories.add("file:${repositoryDir.absolutePath}")

                        val repositoryFeatureArtifacts = project.repositoryFeatureArtifacts
                        dependsOn(repositoryFeatureArtifacts)
                        installUnits.addAll(project.provider {
                            repositoryFeatureArtifacts.resolvedConfiguration.resolvedArtifacts.map {
                                "${it.name}.feature.group"
                            }
                        })

                        this.repositories.addAll(extension.eclipseInstallationAdditionalRepositories)
                        this.installUnits.addAll(extension.eclipseInstallationAdditionalInstallUnits)
                        this.appName.set(extension.eclipseInstallationAppName)

                        this.os.set(os)
                        this.arch.set(arch)
                    }
                createTaskPerOsArch[osArch] = createTask

                val archiveTask =
                    project.tasks.register<ArchiveEclipseInstallation>("archiveEclipseInstallationFor$taskSuffix") {
                        group = "coronium"
                        description =
                            "Archives an Eclipse installation for ${os.displayName} ${arch.displayName}, including the features of this repository"
                        initFromCreateTask(createTask)
                    }
                archiveTaskPerOsArch[osArch] = archiveTask

                val createWithJreTask =
                    project.tasks.register<EclipseInstallationAddJvm>("createEclipseInstallationWithJvmFor$taskSuffix") {
                        group = "coronium"
                        description =
                            "Create an Eclipse installation with embedded JVM for ${os.displayName} ${arch.displayName}, including the features of this repository"
                        this.createTask.set(createTask)
                    }
                createWithJvmTaskPerOsArch[osArch] = createWithJreTask

                val archiveWithJreTask =
                    project.tasks.register<ArchiveEclipseInstallation>("archiveEclipseInstallationWithJvmFor$taskSuffix") {
                        group = "coronium"
                        description =
                            "Archives an Eclipse installation with embedded JVM ${os.displayName} ${arch.displayName}, including the features of this repository"
                        initFromAddJvmTask(createWithJreTask)
                    }
                archiveWithJvmTaskPerOsArch[osArch] = archiveWithJreTask
            }
        }

        // Register tasks that run create/archive tasks for all OS/architecture combinations.
        val createAllTask = project.tasks.register("createEclipseInstallations") {
            group = "coronium"
            description =
                "Creates Eclipse installations for all valid OS/architectures, including the features of this repository"
            createTaskPerOsArch.forEach { dependsOn(it.value) }
        }
        project.tasks.register("archiveEclipseInstallations") {
            group = "coronium"
            description =
                "Archives Eclipse installations for all valid OS/architectures, including the features of this repository"
            dependsOn(createAllTask)
            archiveTaskPerOsArch.forEach { dependsOn(it.value) }
        }
        val createAllWithJreTask = project.tasks.register("createEclipseInstallationsWithJvm") {
            group = "coronium"
            description =
                "Creates Eclipse installations with embedded JVM for all valid OS/architectures, including the features of this repository"
            dependsOn(createAllTask)
            createWithJvmTaskPerOsArch.forEach { dependsOn(it.value) }
        }
        project.tasks.register("archiveEclipseInstallationsWithJvm") {
            group = "coronium"
            description =
                "Archives Eclipse installations with embedded JVM for all valid OS/architectures, including the features of this repository"
            dependsOn(createAllWithJreTask)
            archiveWithJvmTaskPerOsArch.forEach { dependsOn(it.value) }
        }

        // Register tasks that run create/archive tasks for the current OS/architecture.
        val currentOsArch = OsArch(Os.current(), Arch.current())
        val createCurrentTask = project.tasks.register("createEclipseInstallation") {
            group = "coronium"
            description =
                "Creates an Eclipse installation for the current OS/architectures, including the features of this repository"
            dependsOn(createTaskPerOsArch[currentOsArch])
        }
        project.tasks.register("archiveEclipseInstallation") {
            group = "coronium"
            description =
                "Archives an Eclipse installation for the current OS/architecture, including the features of this repository"
            dependsOn(createCurrentTask)
            dependsOn(archiveTaskPerOsArch[currentOsArch])
        }
        val createCurrentWithJreTask = project.tasks.register("createEclipseInstallationWithJvm") {
            group = "coronium"
            description =
                "Creates an Eclipse installation with embedded JVM for the current OS/architecture, including the features of this repository"
            dependsOn(createWithJvmTaskPerOsArch[currentOsArch])
        }
        project.tasks.register("archiveEclipseInstallationWithJvm") {
            group = "coronium"
            description =
                "Archives an Eclipse installation with embedded JVM for the current OS/architecture, including the features of this repository"
            dependsOn(createCurrentWithJreTask)
            dependsOn(archiveWithJvmTaskPerOsArch[currentOsArch])
        }

        // Create a publication from the repository component.
        project.afterEvaluate {
            extension.createPublication.finalizeValue()
            extension.createEclipseInstallationPublications.finalizeValue()
            extension.createEclipseInstallationWithJvmPublications.finalizeValue()
            extension.eclipseInstallationPublicationClassifierPrefix.finalizeValue()
            if (extension.createPublication.get()) {
                project.pluginManager.withPlugin("maven-publish") {
                    project.extensions.configure<PublishingExtension> {
                        publications.register<MavenPublication>("Repository") {
                            from(repositoryComponent)
                            if (extension.createEclipseInstallationPublications.get()) {
                                archiveTaskPerOsArch.forEach { (os, arch), task ->
                                    artifact(task.get()) {
                                        classifier =
                                            "${extension.eclipseInstallationPublicationClassifierPrefix.get()}-${os.p2OsName}-${arch.p2ArchName}"
                                    }
                                }
                            }
                            if (extension.createEclipseInstallationWithJvmPublications.get()) {
                                archiveWithJvmTaskPerOsArch.forEach { (os, arch), task ->
                                    artifact(task.get()) {
                                        classifier =
                                            "${extension.eclipseInstallationPublicationClassifierPrefix.get()}-${os.p2OsName}-${arch.p2ArchName}-jvm"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureRunEclipseTask(project: Project) {
        project.tasks.register<EclipseRun>("runEclipse") {
            group = "coronium"
            description = "Runs the bundles from features in this repository inside an Eclipse instance"

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

private val Project.repositoryFeatureArtifacts get(): Configuration = this.configurations.getByName(RepositoryPlugin.repositoryFeatureArtifacts)
private val Project.repositoryBundleArtifacts get(): Configuration = this.configurations.getByName(RepositoryPlugin.repositoryBundleArtifacts)
