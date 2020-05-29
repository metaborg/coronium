package mb.coronium.model.eclipse

import mb.coronium.util.Log
import java.util.jar.Attributes

data class BundleCoordinates(val name: String, val version: BundleVersion, val isSingleton: Boolean = true) {
  companion object {
    private const val symbolicNameAttribute = "Bundle-SymbolicName"
    private const val versionAttribute = "Bundle-Version"
    private const val singletonParameter = "singleton"

    fun readFromManifestAttributes(attributes: Attributes) =
      Builder().readFromManifestAttributes(attributes).build()
  }

  class Builder {
    var name: String? = null
    var version: BundleVersion? = null
    var isSingleton: Boolean? = null

    fun readFromManifestAttributes(attributes: Attributes): Builder {
      val (name, isSingleton) = run {
        val symbolicName: String? = attributes.getValue(symbolicNameAttribute)
        when {
          // Symbolic name can contain extra data such as: "org.eclipse.core.runtime; singleton:=true". Take everything
          // before the ; as the name. Also search for singleton parameter.
          symbolicName != null && symbolicName.contains(';') -> {
            val split = symbolicName.split(';')
            val isSingleton = split.any { it.contains(singletonParameter) && it.contains("true") }
            Pair(split[0].trim(), isSingleton)
          }
          symbolicName != null -> Pair(symbolicName.trim(), false)
          else -> Pair(null, null)
        }
      }
      if(name != null) {
        this.name = name
      }
      if(isSingleton != null) {
        this.isSingleton = isSingleton
      }

      val version = run {
        val versionStr = attributes.getValue(versionAttribute)?.trim()
        if(versionStr == null) {
          null
        } else {
          BundleVersion.parse(versionStr)
            ?: throw BundleParseException("Cannot read bundle '$name' from manifest, failed to parse $versionAttribute '$versionStr'")
        }
      }
      if(version != null) {
        this.version = version
      }
      return this
    }

    fun build() = BundleCoordinates(
      name ?: error("Cannot create bundle coordinates, name was not set"),
      version ?: error("Cannot create bundle coordinates, version was not set"),
      isSingleton ?: true
    )
  }

  fun writeToManifestAttributes(attributes: MutableMap<String, String>) {
    val symbolicName = if(isSingleton) "$name;$singletonParameter:=true" else name
    attributes[symbolicNameAttribute] = symbolicName
    attributes[versionAttribute] = version.toString()
  }

  override fun toString() = "$name@$version"
}

data class Bundle(
  val manifestVersion: String = manifestVersionDefault,
  val coordinates: BundleCoordinates,
  val requiredBundles: Collection<BundleDependency> = listOf(),
  val fragmentHost: BundleDependency? = null,
  val sourceBundleFor: BundleDependency? = null
) {
  companion object {
    private const val manifestVersionAttribute = "Bundle-ManifestVersion"
    private const val manifestVersionDefault = "2"
    private const val fragmentHostAttribute = "Fragment-Host"
    private const val eclipseSourceBundleAttribute = "Eclipse-SourceBundle"
  }

  class Builder {
    val coordinates: BundleCoordinates.Builder = BundleCoordinates.Builder()
    var manifestVersion: String? = null
    var requiredBundles: MutableCollection<BundleDependency> = mutableListOf()
    var fragmentHost: BundleDependency? = null
    var sourceBundleFor: BundleDependency? = null

    fun readFromManifestAttributes(attributes: Attributes, log: Log): Builder {
      coordinates.readFromManifestAttributes(attributes)

      val manifestVersion: String? = attributes.getValue(manifestVersionAttribute)
      if(manifestVersion != null) {
        this.manifestVersion = manifestVersion
      }

      val requiredBundles = BundleDependency.readRequireBundleFromManifest(attributes, log)
      this.requiredBundles.addAll(requiredBundles)

      val fragmentHost = BundleDependency.readDependencyFromManifest(attributes, fragmentHostAttribute, log)
      if(fragmentHost != null) {
        this.fragmentHost = fragmentHost
      }

      val sourceBundleFor = BundleDependency.readDependencyFromManifest(attributes, eclipseSourceBundleAttribute, log)
      if(sourceBundleFor != null) {
        this.sourceBundleFor = sourceBundleFor
      }

      return this
    }

    fun build() =
      Bundle(manifestVersion
        ?: manifestVersionDefault, coordinates.build(), requiredBundles, fragmentHost, sourceBundleFor)
  }

  fun writeToManifestAttributes(): Map<String, String> {
    val attributes = mutableMapOf<String, String>()
    attributes[manifestVersionAttribute] = manifestVersion
    coordinates.writeToManifestAttributes(attributes)
    BundleDependency.writeToRequireBundleManifestAttributes(requiredBundles, attributes)
    if(fragmentHost != null) {
      BundleDependency.writeToDependencyManifestAttributes(fragmentHost, fragmentHostAttribute, attributes)
    }
    if(sourceBundleFor != null) {
      BundleDependency.writeToDependencyManifestAttributes(sourceBundleFor, eclipseSourceBundleAttribute, attributes)
    }
    return attributes
  }

  override fun toString() = coordinates.toString()
}

class BundleParseException(message: String, cause: Throwable?) : Exception(message, cause) {
  constructor(message: String) : this(message, null)
}

data class BundleDependency(
  val name: String,
  val version: BundleVersionOrRange? = null,
  val resolution: DependencyResolution = DependencyResolution.default,
  val visibility: DependencyVisibility = DependencyVisibility.default,
  val isSourceBundleDependency: Boolean = false
) {
  companion object {
    private const val requireBundleAttribute = "Require-Bundle"

    private const val bundleVersionParameter = "bundle-version"
    private const val versionParameter = "version"
    private const val resolutionParameter = "resolution"
    private const val visibilityParameter = "visibility"

    fun readRequireBundleFromManifest(attributes: Attributes, log: Log): Collection<BundleDependency> {
      val requireBundleStr = attributes.getValue(requireBundleAttribute)
      return if(requireBundleStr != null) {
        try {
          parse(requireBundleStr, log)
        } catch(e: RequireBundleParseException) {
          throw BundleParseException("Cannot read dependency from manifest, failed to parse $requireBundleAttribute '$requireBundleStr'", e)
        }
      } else {
        arrayListOf()
      }
    }

    fun readDependencyFromManifest(attributes: Attributes, attributeName: String, log: Log): BundleDependency? {
      val value = attributes.getValue(attributeName)
      return if(value != null) {
        try {
          parseInner(value, log)
        } catch(e: RequireBundleParseException) {
          throw BundleParseException("Cannot read dependency from manifest, failed to parse $attributeName '$value'", e)
        }
      } else {
        null
      }
    }

    fun writeToRequireBundleManifestAttributes(requiredBundles: Collection<BundleDependency>, attributes: MutableMap<String, String>) {
      if(!requiredBundles.isEmpty()) {
        attributes[requireBundleAttribute] = requiredBundles.joinToString(",") { it.toString() }
      }
    }

    fun writeToDependencyManifestAttributes(dependency: BundleDependency, attributeName: String, attributes: MutableMap<String, String>) {
      attributes[attributeName] = dependency.toString()
    }


    fun parse(str: String, log: Log): Collection<BundleDependency> {
      // Can't split on ',', because it also appears inside quoted version ranges. Manually parse to handle quotes.
      val requiredBundles = mutableListOf<BundleDependency>()
      if(str.isEmpty()) {
        return requiredBundles
      }
      var quoted = false
      var pos = 0
      for(i in 0 until str.length) {
        val char = str[i]
        when {
          char == '"' -> quoted = !quoted
          char == ',' && !quoted -> {
            val inner = str.substring(pos, i)
            val dependency = parseInner(inner, log)
            requiredBundles.add(dependency)
            pos = i + 1
          }
        }
      }
      if(quoted) {
        throw RequireBundleParseException("Failed to parse Require-Bundle string '$str': a quote was not closed")
      }
      if(pos < str.length) {
        val inner = str.substring(pos, str.length)
        val dependency = parseInner(inner, log)
        requiredBundles.add(dependency)
      }
      return requiredBundles
    }

    fun parseInner(str: String, log: Log): BundleDependency {
      val elements = str.split(';')
      if(elements.isEmpty()) {
        throw RequireBundleParseException("Failed to parse part of Require-Bundle string '$str': it does not have a name element")
      }
      val name = elements[0].trim()
      var versionOrRange: BundleVersionOrRange? = null
      var resolution = DependencyResolution.Mandatory
      var visibility = DependencyVisibility.Private
      var isSourceBundleDependency = false
      for(element in elements.subList(1, elements.size)) {
        @Suppress("NAME_SHADOWING") val element = element.trim()
        when {
          element.startsWith(bundleVersionParameter) -> {
            // Expected format: bundle-version="<str>", strip to <str>.
            versionOrRange = BundleVersionOrRange.parse(stripElement(element), log)
          }
          element.startsWith(resolutionParameter) -> {
            // Expected format: resolution:="<str>", strip to <str>.
            resolution = DependencyResolution.parse(stripElement(element))
          }
          element.startsWith(visibilityParameter) -> {
            // Expected format: visibility:="<str>", strip to <str>.
            visibility = DependencyVisibility.parse(stripElement(element))
          }
          // HACK: support parsing Eclipse-SourceBundle by accepting 'version' elements.
          element.startsWith(versionParameter) -> {
            // Expected format: version="<str>", strip to <str>.
            versionOrRange = BundleVersionOrRange.parse(stripElement(element), log)
            isSourceBundleDependency = true
          }
        }
      }
      return BundleDependency(name, versionOrRange, resolution, visibility, isSourceBundleDependency)
    }

    private fun stripElement(element: String): String {
      @Suppress("NAME_SHADOWING") var element = element.substring(element.indexOf('=') + 1).trim()
      if(element.startsWith('"')) {
        element = element.substring(1)
      }
      if(element.endsWith('"')) {
        element = element.substring(0, element.length - 1)
      }
      return element.trim()
    }
  }

  fun mapVersion(func: (BundleVersionOrRange?) -> BundleVersionOrRange?) =
    BundleDependency(name, func(version), resolution, visibility, isSourceBundleDependency)

  override fun toString(): String {
    val parameters = mutableListOf<String>()
    if(version != null) {
      if(isSourceBundleDependency) {
        // HACK: support parsing Eclipse-SourceBundle by accepting 'version' elements.
        parameters.add(";$versionParameter=\"$version\"")
      } else {
        parameters.add(";$bundleVersionParameter=\"$version\"")
      }
    }
    if(resolution != DependencyResolution.default) {
      parameters.add(";$resolutionParameter:=${DependencyResolution.toString(resolution)}")
    }
    if(visibility != DependencyVisibility.default) {
      parameters.add(";$visibilityParameter:=${DependencyVisibility.toString(visibility)}")
    }
    return "$name${parameters.joinToString("")}"
  }
}

class RequireBundleParseException(message: String) : Exception(message)

enum class DependencyResolution {
  Mandatory,
  Optional;

  companion object {
    fun parse(str: String) = when(str) {
      "mandatory" -> Mandatory
      "optional" -> Optional
      else -> Mandatory
    }

    val default get() = Mandatory

    fun toString(resolution: DependencyResolution) = when(resolution) {
      Mandatory -> "mandatory"
      Optional -> "optional"
    }
  }
}

enum class DependencyVisibility {
  Private,
  Reexport;

  companion object {
    fun parse(str: String) = when(str) {
      "private" -> Private
      "reexport" -> Reexport
      else -> Private
    }

    val default get() = Private

    fun toString(visibility: DependencyVisibility) = when(visibility) {
      Private -> "private"
      Reexport -> "reexport"
    }
  }
}

