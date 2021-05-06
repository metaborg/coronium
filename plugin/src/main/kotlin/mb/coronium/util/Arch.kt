package mb.coronium.util

import java.io.Serializable

enum class Arch : Serializable {
  X86_32 {
    override val displayName = "X86"

    override val appDownloadUrlArchiveSuffix = ""

    override val p2ArchName = "x86"

    override val jreDownloadUrlArchiveSuffix = "i586"
  },
  X86_64 {
    override val displayName = "X64"

    override val appDownloadUrlArchiveSuffix = "-x86_64"

    override val p2ArchName = "x86_64"

    override val jreDownloadUrlArchiveSuffix = "x64"
  };

  abstract val displayName: String

  abstract val appDownloadUrlArchiveSuffix: String

  abstract val p2ArchName: String

  abstract val jreDownloadUrlArchiveSuffix: String

  companion object {
    fun current(): Arch {
      val arch = System.getProperty("os.arch")
      return when(arch) {
        "x86", "i386" -> X86_32
        "amd64", "x86_64" -> X86_64
        else -> error("Unsupported Eclipse architecture '$arch'")
      }
    }
  }
}
