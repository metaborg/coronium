package mb.coronium.util

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

fun deleteNonEmptyDirectoryIfExists(directory: Path) {
    if (!Files.exists(directory)) return
    // Delete contents of temporary directory, and the directory itself.
    Files.walk(directory)
        .sorted(Comparator.reverseOrder())
        .forEach { Files.deleteIfExists(it) }
}

fun createParentDirectories(path: Path) {
    val parent = path.parent
    if (parent != null) {
        Files.createDirectories(parent)
    }
}

fun Path.toPortableString(): String {
    if (FileSystems.getDefault().separator == """\""") {
        return this.toString().replace('\\', '/')
    }
    return this.toString()
}

fun File.toPortableString(): String {
    if (File.separatorChar == '\\') {
        return this.toString().replace('\\', '/')
    }
    return this.toString()
}
