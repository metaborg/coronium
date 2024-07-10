package mb.coronium.util

import java.io.Serializable
import java.nio.file.Path
import java.nio.file.Paths

enum class Os : Serializable {
    Windows {
        override val validArchs = listOf(Arch.X86_64)
        override val displayName = "Windows"

        override val eclipseDownloadUrlArchiveSuffix = "win32"
        override val eclipseDownloadUrlArchiveExtension = "zip"
        override val eclipsePluginsDir = Paths.get("eclipse", "plugins")
        override val eclipseConfigurationDir = Paths.get("eclipse", "configuration")
        override val eclipseExtraJvmArgs: List<String> = listOf()

        override val p2OsName = "win32"
        override val p2WsName = "win32"

        override val installationIniRelativePath = "eclipse.ini"
        override fun installationJvmRelativePath(arch: Arch) = when (arch) {
            Arch.X86_32 -> """jvm\bin\client\jvm.dll"""
            Arch.X86_64 -> """jvm\bin\server\jvm.dll"""
            Arch.AArch64 -> error("Unsupported architecture aarch64 on Windows")
        }

        override val jvmDownloadUrlArchiveSuffix = "windows"
        override val jvmDownloadUrlArchiveExtension = "zip"
    },
    Linux {
        override val validArchs = listOf(Arch.X86_64, Arch.AArch64)
        override val displayName = "Linux"

        override val eclipseDownloadUrlArchiveSuffix = "linux-gtk"
        override val eclipseDownloadUrlArchiveExtension = "tar.gz"
        override val eclipsePluginsDir = Paths.get("eclipse", "plugins")
        override val eclipseConfigurationDir = Paths.get("eclipse", "configuration")
        override val eclipseExtraJvmArgs: List<String> = listOf()

        override val p2OsName = "linux"
        override val p2WsName = "gtk"

        override val installationIniRelativePath = "eclipse.ini"
        override fun installationJvmRelativePath(arch: Arch) = "jvm/bin/java"

        override val jvmDownloadUrlArchiveSuffix = "linux"
        override val jvmDownloadUrlArchiveExtension = "tar.gz"
    },
    OSX {
        override val validArchs = listOf(Arch.X86_64, Arch.AArch64)
        override val displayName = "MacOSX"

        override val eclipseDownloadUrlArchiveSuffix = "macosx-cocoa"
        override val eclipseDownloadUrlArchiveExtension = "dmg"
        override val eclipsePluginsDir = Paths.get("Eclipse.app", "Contents", "Eclipse", "plugins")
        override val eclipseConfigurationDir = Paths.get("Eclipse.app", "Contents", "Eclipse", "configuration")
        override val eclipseExtraJvmArgs: List<String> = listOf("-XstartOnFirstThread")

        override val p2OsName = "macosx"
        override val p2WsName = "cocoa"

        override val installationIniRelativePath = "Contents/Eclipse/eclipse.ini"
        override fun installationJvmRelativePath(arch: Arch) = "../../jvm/Contents/Home/bin/java"

        override val jvmDownloadUrlArchiveSuffix = "macosx"
        override val jvmDownloadUrlArchiveExtension = "tar.gz"
    };

    abstract val validArchs: Iterable<Arch>
    abstract val displayName: String

    abstract val eclipseDownloadUrlArchiveSuffix: String
    abstract val eclipseDownloadUrlArchiveExtension: String
    abstract val eclipsePluginsDir: Path
    abstract val eclipseConfigurationDir: Path
    abstract val eclipseExtraJvmArgs: List<String>

    abstract val p2OsName: String
    abstract val p2WsName: String

    abstract val installationIniRelativePath: String
    abstract fun installationJvmRelativePath(arch: Arch): String

    abstract val jvmDownloadUrlArchiveSuffix: String
    abstract val jvmDownloadUrlArchiveExtension: String

    companion object {
        fun current(): Os {
            val os = System.getProperty("os.name")
            return when {
                os.substring(0, 5).equals("linux", true) -> Linux
                os.substring(0, 7).equals("windows", true) -> Windows
                os.equals("Mac OS X", true) -> OSX
                else -> error("Unsupported Eclipse OS '$os'")
            }
        }
    }
}
