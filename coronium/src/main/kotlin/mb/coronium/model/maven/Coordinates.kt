package mb.coronium.model.maven

import java.nio.file.Paths

/**
 * Maven artifact co-ordinates.
 */
data class Coordinates(
    val groupId: String,
    val id: String,
    val version: MavenVersion,
    val classifier: String? = null,
    val extension: String? = null
) {
    fun withExtension(newExtension: String?): Coordinates {
        return Coordinates(groupId, id, version, classifier, newExtension)
    }

    fun toPath() = Paths.get(
        "$groupId-$id-$version" +
          (if (classifier != null) "-$classifier" else "") +
          if (extension != null) ".$extension" else ""
    )

    override fun toString() = "$groupId:$id:$version" +
      (if (classifier != null) ":$classifier" else "") +
      if (extension != null) ".$extension" else ""
}

/**
 * Maven dependency co-ordinates.
 */
data class DependencyCoordinates(
    val groupId: String,
    val id: String,
    val version: MavenVersionOrRange,
    val classifier: String? = null,
    val extension: String? = null
) {
    override fun toString() = "$groupId:$id:$version" +
      (if (classifier != null) ":$classifier" else "") +
      if (extension != null) ".$extension" else ""
}
