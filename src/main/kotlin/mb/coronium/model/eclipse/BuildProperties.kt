package mb.coronium.model.eclipse

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

data class BuildProperties(
  val sourceDirs: Collection<String>,
  val outputDir: String?,
  val binaryIncludes: Collection<String>
) {
  class Builder {
    val sourceDirs: MutableCollection<String> = mutableListOf()
    var outputDir: String? = null
    val binaryIncludes: MutableCollection<String> = mutableListOf()

    fun readFromPropertiesFile(propertiesFile: Path) {
      Files.newInputStream(propertiesFile).buffered().use { inputStream ->
        readFromPropertiesStream(inputStream)
      }
    }

    fun readFromPropertiesStream(propertiesInputStream: InputStream) {
      val properties = Properties()
      properties.load(propertiesInputStream)
      readFromProperties(properties)
    }

    fun readFromProperties(properties: Properties) {
      val sourceDirsStr = properties.getProperty("source..")
      if(sourceDirsStr != null) {
        sourceDirs.addAll(sourceDirsStr.split(','))
      }
      outputDir = properties.getProperty("output..")
      val binaryIncludesStr = properties.getProperty("bin.includes")
      if(binaryIncludesStr != null) {
        binaryIncludes.addAll(binaryIncludesStr.split(','))
      }
    }

    fun build() =
      BuildProperties(sourceDirs, outputDir, this.binaryIncludes)
  }
}