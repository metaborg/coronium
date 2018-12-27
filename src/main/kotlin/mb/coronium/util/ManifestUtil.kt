package mb.coronium.util

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import java.util.jar.Manifest

fun readManifestFromFile(file: Path): Manifest {
  return Files.newInputStream(file).buffered().use { inputStream ->
    Manifest(inputStream)
  }
}

fun readManifestFromJarFileOrDirectory(jarFileOrDir: Path): Manifest {
  return when {
    !Files.exists(jarFileOrDir) -> {
      throw IOException("Bundle file or directory $jarFileOrDir does not exist")
    }
    Files.isDirectory(jarFileOrDir) -> {
      val manifestFile = jarFileOrDir.resolve(Paths.get("META-INF", "MANIFEST.MF"))
      Files.newInputStream(manifestFile).buffered().use { inputStream ->
        Manifest(inputStream)
      }
    }
    else -> {
      Files.newInputStream(jarFileOrDir).buffered().use { inputStream ->
        JarInputStream(inputStream).manifest
      } ?: throw IOException("Could not get bundle manifest in JAR file $jarFileOrDir")
    }
  }
}


fun Attributes.toStringMap(): Map<String, String> {
  return entries.map { it.key.toString() to it.value.toString() }.toMap()
}
