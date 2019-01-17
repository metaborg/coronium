package mb.coronium.model.eclipse

import mb.coronium.util.Log
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
  val dependencies: Collection<Dependency>
) {
  data class Dependency(
    val coordinates: Coordinates,
    val unpack: Boolean
  ) {
    data class Coordinates(val id: String, val version: BundleVersion)

    fun mapVersion(func: (BundleVersion) -> BundleVersion) = Dependency(Coordinates(coordinates.id, func(coordinates.version)), unpack)
  }

  class Builder {
    var id: String? = null
    var version: BundleVersion? = null
    var label: String? = null
    var dependencies: MutableCollection<Dependency> = mutableListOf()

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

      val subNodes = featureNode.childNodes
      for(i in 0 until subNodes.length) {
        val subNode = subNodes.item(i)
        if(subNode.nodeType != Node.ELEMENT_NODE) {
          continue
        }
        when(subNode.nodeName) {
          "plugin" -> {
            val depId = subNode.attributes.getNamedItem("id")?.nodeValue
            val depVersionStr = subNode.attributes.getNamedItem("version")?.nodeValue
            if(depId == null || depVersionStr == null) {
              log.warning("Plugin dependency of $file has no id or version; skipping dependency")
            } else {
              val depVersion = BundleVersion.parse(depVersionStr)
              if(depVersion == null) {
                log.warning("Could not parse dependency version '$depVersionStr' of $file; skipping dependency")
              } else {
                val coordinates = Dependency.Coordinates(depId, depVersion)
                val unpack = subNode.attributes.getNamedItem("unpack")?.nodeValue?.toBoolean() ?: false
                dependencies.add(Dependency(coordinates, unpack))
              }
            }
          }
          else -> {
            // TODO: full support for feature.xml. See: https://help.eclipse.org/photon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Freference%2Fmisc%2Ffeature_manifest.html.
            log.warning("Unsupported subnode ${subNode.nodeName} of $file; skipping subnode")
          }
        }
      }
    }

    fun build() = Feature(
      id ?: error("Cannot create feature, id was not set"),
      version ?: error("Cannot create feature, version was not set"),
      label,
      dependencies
    )
  }

  fun writeToFeatureXml(outputStream: OutputStream) {
    PrintWriter(outputStream).use { writer ->
      writer.println("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.println("""<feature id="$id" version="$version"${if(label != null) """ label="$label"""" else ""}>""")
      for(dependency in dependencies) {
        writer.println("""  <plugin id="${dependency.coordinates.id}" version="${dependency.coordinates.version}" unpack="${dependency.unpack}"/>""")
      }
      writer.println("</feature>")
      writer.flush()
    }
  }
}
