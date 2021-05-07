package mb.coronium.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.nio.file.Path

@Suppress("UnstableApiUsage")
open class ArchiveEclipseInstallation : Zip() {
  @get:Internal
  val srcTask: Property<TaskProvider<*>> = project.objects.property()

  @get:Input
  val archiveName: Property<String> = project.objects.property()

  @get:InputDirectory
  val srcDir: Property<Path> = project.objects.property()

  @get:Input
  val appDirName: Property<String> = project.objects.property()

  init {
    dependsOn(srcTask)
    archiveFileName.set(archiveName.map { "$it.zip" })
    destinationDirectory.set(project.buildDir.resolve("dist"))
    from(srcDir)
    doFirst {
      include("${appDirName.get()}/**")
    }
  }

  fun initFromCreateTask(createTask: TaskProvider<EclipseCreateInstallation>) {
    srcTask.set(createTask)
    archiveName.set(createTask.flatMap { ct -> ct.appName.flatMap { ct.os.flatMap { os -> ct.arch.map { arch -> "$it-${os.p2OsName}-${arch.p2ArchName}" } } } })
    srcDir.set(createTask.flatMap { it.destination })
    appDirName.set(createTask.flatMap { it.appDirName })
  }

  fun initFromAddJvmTask(addJvmTask: TaskProvider<EclipseInstallationAddJvm>) {
    srcTask.set(addJvmTask)
    val createTask = addJvmTask.flatMap { it.createTask }
    archiveName.set(createTask.flatMap { it.flatMap { createTask -> createTask.appName.flatMap { createTask.os.flatMap { os -> createTask.arch.map { arch -> "$it-${os.p2OsName}-${arch.p2ArchName}-jvm" } } } } })
    srcDir.set(addJvmTask.flatMap { it.copyDestDir })
    appDirName.set(createTask.flatMap { it.flatMap { it.appDirName } })
  }
}
