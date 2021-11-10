package mb.coronium.model.eclipse

import mb.coronium.mavenize.toEclipse
import mb.coronium.model.maven.MavenVersion
import mb.coronium.util.Log
import org.gradle.api.artifacts.ResolvedConfiguration
import org.w3c.dom.Node
import java.io.OutputStream
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

data class Feature(
  val id: String,
  val version: BundleVersion,
  val label: String?,
  val provider: String?,
  val bundleIncludes: Collection<BundleInclude>,
  val featureIncludes: Collection<FeatureInclude>
) {
  data class BundleInclude(
    val coordinates: Coordinates,
    val unpack: Boolean
  ) {
    data class Coordinates(val id: String, val version: BundleVersion?)

    fun mapVersion(func: (BundleVersion) -> BundleVersion) =
      BundleInclude(Coordinates(coordinates.id, if(coordinates.version == null) coordinates.version else func(coordinates.version)), unpack)
  }

  data class FeatureInclude(
    val coordinates: Coordinates,
    val optional: Boolean
  ) {
    data class Coordinates(val id: String, val version: BundleVersion?)

    fun mapVersion(func: (BundleVersion) -> BundleVersion) =
      FeatureInclude(Coordinates(coordinates.id, if(coordinates.version == null) coordinates.version else func(coordinates.version)), optional)
  }

  class Builder {
    var id: String? = null
    var version: BundleVersion? = null
    var label: String? = null
    var provider: String? = null
    var bundleIncludes: MutableCollection<BundleInclude> = mutableListOf()
    var featureIncludes: MutableCollection<FeatureInclude> = mutableListOf()

    fun readFromFeatureXml(file: Path, log: Log) {
      val doc = Files.newInputStream(file).buffered().use { inputStream ->
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        builder.parse(inputStream)
      }

      val featureNode = doc.firstChild ?: run {
        log.warning("Feature XML '$file' has no root node; skipping")
        return
      }

      id = featureNode.attributes.getNamedItem("id")?.nodeValue

      val versionStr = featureNode.attributes.getNamedItem("version")?.nodeValue
      if(versionStr != null) {
        version = BundleVersion.parse(versionStr)
        if(version == null) {
          log.warning("Could not parse version '$versionStr' of $file; skipping version")
        }
      }

      label = featureNode.attributes.getNamedItem("label")?.nodeValue
      provider = featureNode.attributes.getNamedItem("provider-name")?.nodeValue

      val subNodes = featureNode.childNodes
      for(i in 0 until subNodes.length) {
        val subNode = subNodes.item(i)
        if(subNode.nodeType != Node.ELEMENT_NODE) {
          continue
        }
        when(subNode.nodeName) {
          "plugin" -> {
            val depId = subNode.attributes.getNamedItem("id")?.nodeValue

            if(depId == null) {
              log.warning("Skipping plugin without id in feature.xml")
            } else {
              val depVersionStr = subNode.attributes.getNamedItem("version")?.nodeValue
              val unpack = subNode.attributes.getNamedItem("unpack")?.nodeValue?.toBoolean() ?: false
              val depVersion = parseVersionStr(depVersionStr, log)
              val coordinates = BundleInclude.Coordinates(depId, depVersion)
              bundleIncludes.add(BundleInclude(coordinates, unpack))
            }
          }
          "includes" -> {
            val depId = subNode.attributes.getNamedItem("id")?.nodeValue

            if(depId == null) {
              log.warning("Skipping feature include without id in feature.xml")
            } else {
              val depVersionStr = subNode.attributes.getNamedItem("version")?.nodeValue
              val optional = subNode.attributes.getNamedItem("optional")?.nodeValue?.toBoolean() ?: false
              val depVersion = parseVersionStr(depVersionStr, log)
              val coordinates = FeatureInclude.Coordinates(depId, depVersion)
              featureIncludes.add(FeatureInclude(coordinates, optional))
            }
          }
          else -> {
            // TODO: full support for feature.xml. See: https://help.eclipse.org/photon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fmisc%2Ffeature_manifest.html.
            log.warning("Unsupported subnode ${subNode.nodeName} of $file; skipping subnode")
          }
        }
      }
    }

    private fun parseVersionStr(depVersionStr: String?, log: Log): BundleVersion? {
      if(depVersionStr == null) {
        return null
      }

      val parseVersionStr = BundleVersion.parse(depVersionStr)
      if(parseVersionStr == null) {
        log.warning("Could not parse dependency version '$depVersionStr'; skipping dependency")
        return null
      }

      return parseVersionStr
    }

    fun build() = Feature(
      id ?: error("Cannot create feature, id was not set"),
      version ?: error("Cannot create feature, version was not set"),
      label,
      provider,
      bundleIncludes,
      featureIncludes
    )
  }

  fun mergeWith(gradleBundleIncludes: ResolvedConfiguration, gradleFeatureIncludes: ResolvedConfiguration): Feature {
    val mergedBundleIncludes: MutableCollection<BundleInclude> = mutableListOf()
    val mergedFeatureIncludes: MutableCollection<FeatureInclude> = mutableListOf()

    // Add all Gradle dependencies or merge its version with an existing feature dependency
    gradleBundleIncludes.resolvedArtifacts.forEach { resolvedArtifact ->
      val module = resolvedArtifact.moduleVersion.id
      val existingDependency = bundleIncludes.find { it.coordinates.id == module.name }
      val dependencyVersion = MavenVersion.parse(module.version).toEclipse()
      if(existingDependency == null) {
        val coordinates = BundleInclude.Coordinates(module.name, dependencyVersion)
        val mergedDependency = BundleInclude(coordinates, false)
        mergedBundleIncludes.add(mergedDependency)
      } else {
        val coordinates = BundleInclude.Coordinates(existingDependency.coordinates.id, dependencyVersion)
        val mergedDependency = BundleInclude(coordinates, existingDependency.unpack)
        mergedBundleIncludes.add(mergedDependency)
      }
    }
    gradleFeatureIncludes.resolvedArtifacts.forEach { resolvedArtifact ->
      val module = resolvedArtifact.moduleVersion.id
      val existingDependency = bundleIncludes.find { it.coordinates.id == module.name }
      val dependencyVersion = MavenVersion.parse(module.version).toEclipse()
      if(existingDependency == null) {
        val coordinates = FeatureInclude.Coordinates(module.name, dependencyVersion)
        val mergedDependency = FeatureInclude(coordinates, false)
        mergedFeatureIncludes.add(mergedDependency)
      } else {
        val coordinates = FeatureInclude.Coordinates(existingDependency.coordinates.id, dependencyVersion)
        val mergedDependency = FeatureInclude(coordinates, existingDependency.unpack)
        mergedFeatureIncludes.add(mergedDependency)
      }
    }

    // Add all includes not found in the Gradle configurations.
    bundleIncludes.forEach { bundleInclude ->
      if(!gradleBundleIncludes.resolvedArtifacts.any { it.name == bundleInclude.coordinates.id }) {
        mergedBundleIncludes.add(bundleInclude)
      }
    }
    featureIncludes.forEach { featureInclude ->
      if(!gradleFeatureIncludes.resolvedArtifacts.any { it.name == featureInclude.coordinates.id }) {
        mergedFeatureIncludes.add(featureInclude)
      }
    }

    return Feature(id, version, label, provider, mergedBundleIncludes, mergedFeatureIncludes)
  }

  fun writeToFeatureXml(outputStream: OutputStream) {
    PrintWriter(outputStream).use { writer ->
      writer.println("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.println("""<feature id="$id" version="$version"${if(label != null) """ label="$label"""" else ""}${if(provider != null) """ provider-name="$provider"""" else ""}>""")
      for(bundleInclude in bundleIncludes) {
        writer.println("""  <plugin id="${bundleInclude.coordinates.id}" version="${bundleInclude.coordinates.version}" unpack="${bundleInclude.unpack}"/>""")
      }
      for(featureInclude in featureIncludes) {
        writer.println("""  <includes id="${featureInclude.coordinates.id}" version="${featureInclude.coordinates.version}" optional="${featureInclude.optional}"/>""")
      }
      writer.println("</feature>")
      writer.flush()
    }
  }
}
