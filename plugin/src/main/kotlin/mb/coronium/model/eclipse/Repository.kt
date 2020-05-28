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

data class Repository(
  val dependencies: Collection<Dependency>,
  val categories: Collection<Category>
) {
  data class Dependency(
    val coordinates: Coordinates,
    val categoryName: String?
  ) {
    data class Coordinates(val id: String, val version: BundleVersion)
  }

  data class Category(
    val name: String,
    val label: String?,
    val description: String?
  )

  class Builder {
    val dependencies: MutableCollection<Dependency> = mutableListOf()
    val categories: MutableCollection<Category> = mutableListOf()

    fun readFromRepositoryXml(file: Path, log: Log) {
      val doc = Files.newInputStream(file).buffered().use { inputStream ->
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        builder.parse(inputStream)
      }

      val siteNode = doc.firstChild ?: run {
        log.warning("Repository XML '$file' has no root node; skipping")
        return
      }

      val subNodes = siteNode.childNodes
      for(i in 0 until subNodes.length) {
        val subNode = subNodes.item(i)
        if(subNode.nodeType != Node.ELEMENT_NODE) {
          continue
        }

        when(subNode.nodeName) {
          "feature" -> {
            val depId = subNode.attributes.getNamedItem("id")?.nodeValue
            val depVersionStr = subNode.attributes.getNamedItem("version")?.nodeValue
            if(depId == null || depVersionStr == null) {
              log.warning("Feature dependency of $file has no id or version; skipping dependency")
            } else {
              val depVersion = BundleVersion.parse(depVersionStr)
              if(depVersion == null) {
                log.warning("Could not parse dependency version '$depVersionStr' of $file; skipping dependency")
              } else {
                val coordinates = Dependency.Coordinates(depId, depVersion)
                val categoryName = featureCategoryName(subNode, file, log)
                dependencies.add(Dependency(coordinates, categoryName))
              }
            }
          }
          "category-def" -> {
            val name = subNode.attributes.getNamedItem("name")?.nodeValue
            if(name == null) {
              log.warning("Category definition of $file has no name; skipping category definition")
            } else {
              val label = subNode.attributes.getNamedItem("label")?.nodeValue
              val description = categoryDescription(subNode, file, log)
              categories.add(Category(name, label, description))
            }
          }
          else -> {
            // TODO: full support for category.xml or site.xml. See https://wiki.eclipse.org/Tycho/category.xml.
            log.warning("Unsupported subnode ${subNode.nodeName} of $file; skipping subnode")
          }
        }
      }
    }

    private fun featureCategoryName(node: Node, file: Path, log: Log): String? {
      val subNodes = node.childNodes
      for(i in 0 until subNodes.length) {
        val subNode = subNodes.item(i)
        if(subNode.nodeType != Node.ELEMENT_NODE) {
          continue
        }
        when(subNode.nodeName) {
          "category" -> {
            return subNode.attributes.getNamedItem("name")?.nodeValue
          }
          else -> {
            log.warning("Unsupported subnode ${subNode.nodeName} of feature dependency of $file; skipping subnode")
          }
        }
      }
      return null
    }

    private fun categoryDescription(node: Node, file: Path, log: Log): String? {
      val subNodes = node.childNodes
      for(i in 0 until subNodes.length) {
        val subNode = subNodes.item(i)
        if(subNode.nodeType != Node.ELEMENT_NODE) {
          continue
        }
        when(subNode.nodeName) {
          "description" -> {
            return subNode.textContent
          }
          else -> {
            log.warning("Unsupported subnode ${subNode.nodeName} of category definition of $file; skipping subnode")
          }
        }
      }
      return null
    }

    fun build() = Repository(dependencies, categories)
  }

  fun mergeWith(gradleFeatureDependencies: ResolvedConfiguration): Repository {
    val mergedDependencies: MutableCollection<Dependency> = mutableListOf()

    // Add all Gradle dependencies or merge its version with an existing feature dependency
    gradleFeatureDependencies.resolvedArtifacts.forEach { resolvedArtifact ->
      val module = resolvedArtifact.moduleVersion.id

      val existingDependency = dependencies.find { it.coordinates.id == module.name }
      val dependencyVersion = MavenVersion.parse(module.version).toEclipse()

      if(existingDependency == null) {
        val coordinates = Dependency.Coordinates(module.name, dependencyVersion)
        val mergedDependency = Dependency(coordinates, null)
        mergedDependencies.add(mergedDependency)
      } else {
        val coordinates = Dependency.Coordinates(existingDependency.coordinates.id, dependencyVersion)
        val mergedDependency = Dependency(coordinates, existingDependency.categoryName)
        mergedDependencies.add(mergedDependency)
      }
    }

    // Add all feature dependencies that are not also Gradle dependencies
    dependencies.forEach { featureDependency ->
      if(!gradleFeatureDependencies.resolvedArtifacts.any { it.name == featureDependency.coordinates.id }) {
        mergedDependencies.add(featureDependency)
      }
    }

    return Repository(mergedDependencies, categories)
  }

  fun writeToRepositoryXml(outputStream: OutputStream) {
    PrintWriter(outputStream).use { writer ->
      writer.println("""<?xml version="1.0" encoding="UTF-8"?>""")
      writer.println("""<site>""")
      for(dependency in dependencies) {
        writer.println("""  <feature id="${dependency.coordinates.id}" version="${dependency.coordinates.version}"${if(dependency.categoryName == null) "/" else ""}>""")
        if(dependency.categoryName != null) {
          writer.println("""    <category name="${dependency.categoryName}"/>""")
          writer.println("""  </feature>""")
        }
      }
      for(category in categories) {
        writer.println("""  <category-def name="${category.name}"${if(category.label != null) """ label="${category.label}""" else ""}"${if(category.description == null) "/" else ""}>""")
        if(category.description != null) {
          writer.println("""    <description>${category.description}</description>""")
          writer.println("""  </category-def>""")
        }
      }
      writer.println("</site>")
      writer.flush()
    }
  }
}
