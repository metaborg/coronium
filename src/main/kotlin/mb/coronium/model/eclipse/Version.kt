package mb.coronium.model.eclipse

import mb.coronium.util.Log
import java.util.regex.Pattern

data class BundleVersion(
  val major: Int,
  val minor: Int?,
  val micro: Int?,
  val qualifier: String?
) : BundleVersionOrRange() {
  companion object {
    private val pattern = Pattern.compile("""(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(.+))?""")

    fun parse(str: String): BundleVersion? {
      val matcher = pattern.matcher(str)
      if(!matcher.matches()) return null
      val major = matcher.group(1)?.toInt() ?: return null
      val minor = matcher.group(2)?.toInt()
      val micro = matcher.group(3)?.toInt()
      val qualifier = matcher.group(4)
      return BundleVersion(major, minor, micro, qualifier)
    }

    fun zero() = BundleVersion(0, null, null, null)
  }

  fun withoutQualifier() = BundleVersion(major, minor, micro, null)

  override fun toString() = "$major" +
    (if(minor != null) ".$minor" else "") +
    (if(micro != null) ".$micro" else "") +
    if(qualifier != null) ".$qualifier" else ""
}

data class BundleVersionRange(
  val minInclusive: Boolean,
  val minVersion: BundleVersion,
  val maxVersion: BundleVersion,
  val maxInclusive: Boolean
) : BundleVersionOrRange() {
  companion object {
    private val pattern = Pattern.compile("""([\[\(])\s*([^,]+)\s*,\s*([^,]+)\s*([\]\)])""")

    fun parse(str: String): BundleVersionRange? {
      val matcher = pattern.matcher(str)
      if(!matcher.matches()) return null
      val minChr = matcher.group(1) ?: return null
      val minVerStr = matcher.group(2) ?: return null
      val minVer = BundleVersion.parse(minVerStr) ?: return null
      val maxVerStr = matcher.group(3) ?: return null
      val maxVer = BundleVersion.parse(maxVerStr) ?: return null
      val maxChr = matcher.group(4) ?: return null
      return BundleVersionRange(minChr == "[", minVer, maxVer, maxChr == "]")
    }
  }

  fun withoutQualifiers() =
    BundleVersionRange(minInclusive, minVersion.withoutQualifier(), maxVersion.withoutQualifier(), maxInclusive)

  override fun toString() =
    "${if(minInclusive) "[" else "("}$minVersion,$maxVersion${if(maxInclusive) "]" else ")"}"
}

sealed class BundleVersionOrRange {
  companion object {
    fun parse(str: String, log: Log): BundleVersionOrRange? {
      val parsedVersion = BundleVersion.parse(str)
      if(parsedVersion != null) {
        return parsedVersion
      }
      val parsedVersionRange = BundleVersionRange.parse(str)
      if(parsedVersionRange != null) {
        return parsedVersionRange
      }
      log.warning("Failed to parse version or version range '$str', defaulting to no version (matches any version)")
      return null
    }
  }
}
