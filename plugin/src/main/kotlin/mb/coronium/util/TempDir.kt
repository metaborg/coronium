package mb.coronium.util

import java.io.Closeable
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute

/**
 * A temporary directory that is deleted when [close]d.
 */
class TempDir(prefix: String) : Closeable {
    /**
     * Path to the temporary directory.
     */
    val path: Path = Files.createTempDirectory(prefix)

    /**
     * Create a temporary file inside the temporary directory.
     */
    fun createTempFile(prefix: String, suffix: String, vararg attrs: FileAttribute<*>): Path {
        return Files.createTempFile(path, prefix, suffix, *attrs)
    }

    /**
     * Creates a temporary directory inside the temporary directory.
     */
    fun createTempDir(prefix: String, vararg attrs: FileAttribute<*>): Path {
        return Files.createTempDirectory(path, prefix, *attrs)
    }

    /**
     * Closes the temporary directory, deleting it and its content.
     */
    override fun close() {
        try {
            deleteNonEmptyDirectoryIfExists(path)
        } catch (e: DirectoryNotEmptyException) {
            // For some reason, this exception is thrown even though the directory is empty. Ignore it.
        }
    }
}
