package mb.coronium.model.maven

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * A Maven artifact.
 */
data class MavenArtifact(
  val coordinates: Coordinates,
  val dependencies: Collection<MavenDependency>
) {
  override fun toString() = coordinates.toString()
}

/**
 * An installable Maven artifact.
 */
data class InstallableMavenArtifact(
  val primaryArtifact: PrimaryArtifact,
  val subArtifacts: Collection<SubArtifact>,
  val dependencies: Collection<MavenDependency>
) {
  override fun toString() = primaryArtifact.toString()
}

/**
 * The primary artifact of an installable Maven artifact.
 */
data class PrimaryArtifact(
  val coordinates: Coordinates,
  val file: Path
)

/**
 * A sub-artifact of an installable Maven artifact.
 */
data class SubArtifact(
  val classifier: String?,
  val extension: String?,
  val file: Path
) {
  override fun toString() = "${classifier ?: ""}${if(extension != null) ".$extension" else ""}@$file"
}

/**
 * A Maven dependency.
 */
data class MavenDependency(
  val coordinates: DependencyCoordinates,
  val scope: String?,
  val optional: Boolean
) {
  override fun toString() = coordinates.toString()
}

/**
 * Creates an installable POM sub-artifact by writing a POM XML file into [pomFile], using [coordinates] and
 * [dependencies] to create the POM XML.
 */
fun createPomSubArtifact(pomFile: Path, coordinates: Coordinates, dependencies: Collection<MavenDependency>): SubArtifact {
  Files.newOutputStream(pomFile).buffered().use { outputStream ->
    PrintWriter(outputStream).use { writer ->
      writer.println("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.println("""<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">""")
      writer.println("""  <modelVersion>4.0.0</modelVersion>""")

      writer.println("""  <groupId>${coordinates.groupId}</groupId>""")
      writer.println("""  <artifactId>${coordinates.id}</artifactId>""")
      writer.println("""  <version>${coordinates.version}</version>""")
      if(coordinates.classifier != null) {
        writer.println("""  <classifier>${coordinates.classifier}</classifier>""")
      }
      if(coordinates.extension != null) {
        writer.println("""  <packaging>${coordinates.extension}</packaging>""")
      }

      if(!dependencies.isEmpty()) {
        writer.println()
        writer.println("""  <dependencies>""")
        for(dependency in dependencies) {
          writer.println("""    <dependency>""")
          writer.println("""      <groupId>${dependency.coordinates.groupId}</groupId>""")
          writer.println("""      <artifactId>${dependency.coordinates.id}</artifactId>""")
          writer.println("""      <version>${dependency.coordinates.version}</version>""")
          if(dependency.coordinates.classifier != null) {
            writer.println("""      <classifier>${dependency.coordinates.classifier}</classifier>""")
          }
          if(dependency.coordinates.extension != null) {
            writer.println("""      <packaging>${dependency.coordinates.extension}</packaging>""")
          }
          if(dependency.scope != null) {
            writer.println("""      <scope>${dependency.scope}</scope>""")
          }
          if(dependency.optional) {
            writer.println("""      <optional>true</optional>""")
          }
          writer.println("""    </dependency>""")
        }
        writer.println("""  </dependencies>""")
      }

      writer.println("""</project>""")
      writer.flush()
    }
    outputStream.flush()
  }
  return SubArtifact(null, "pom", pomFile)
}
