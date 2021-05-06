package mb.coronium.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.nio.file.Path

open class ArchiveEclipseInstallation : Zip() {
  @get:Internal
  val sourceTask: Property<TaskProvider<*>> = project.objects.property()

  @get:Input
  val archiveName: Property<String> = project.objects.property()

  @get:InputDirectory
  val sourceDirectory: Property<Path> = project.objects.property()

  @get:Input
  val applicationDirectoryName: Property<String> = project.objects.property()

  init {
    dependsOn(sourceTask)
    archiveFileName.set(archiveName.map { "$it.zip" })
    destinationDirectory.set(project.buildDir.resolve("dist"))
    from(sourceDirectory)
    doFirst {
      include("${applicationDirectoryName.get()}/**")
    }
  }

  fun initFromCreateTask(createTask: TaskProvider<EclipseCreateInstallation>) {
    sourceTask.set(createTask)
    archiveName.set(createTask.flatMap { createTask -> createTask.applicationName.flatMap { createTask.os.flatMap { os -> createTask.arch.map { arch -> "$it-${os.p2OsName}-${arch.p2ArchName}" } } } })
    sourceDirectory.set(createTask.flatMap { it.destination })
    applicationDirectoryName.set(createTask.flatMap { it.applicationDirectoryName })
  }

  fun initFromAddJreTask(addJreTask: TaskProvider<EclipseInstallationAddJre>) {
    sourceTask.set(addJreTask)
    val createTask = addJreTask.flatMap { it.createTask }
    archiveName.set(createTask.flatMap { it.flatMap { createTask -> createTask.applicationName.flatMap { createTask.os.flatMap { os -> createTask.arch.map { arch -> "$it-${os.p2OsName}-${arch.p2ArchName}-jre" } } } } })
    sourceDirectory.set(addJreTask.flatMap { it.destDir })
    applicationDirectoryName.set(createTask.flatMap { it.flatMap { it.applicationDirectoryName } })
  }
}
