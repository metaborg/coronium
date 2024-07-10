package mb.coronium.util

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

/**
 * Unarchives [archiveFile] into [unpackDirectory].
 */
fun unarchive(archiveFile: Path, unpackDirectory: Path, log: Log) {
    val path = archiveFile.toString()
    when {
        path.endsWith(".dmg") -> unarchiveDmgApps(archiveFile, unpackDirectory)
        else -> Files.newInputStream(archiveFile).buffered().use { inputStream ->
            when {
                path.endsWith(".tar.gz") || path.endsWith(".gz") -> {
                    GzipCompressorInputStream(inputStream).buffered().use { compressorInputStream ->
                        unarchive(compressorInputStream, unpackDirectory, log)
                    }
                }

                else -> {
                    unarchive(inputStream, unpackDirectory, log)
                }
            }
        }
    }
}

private fun unarchive(inputStream: InputStream, unpackDirectory: Path, log: Log) {
    Files.createDirectories(unpackDirectory)
    ArchiveStreamFactory().createArchiveInputStream(inputStream).use { archiveInputStream ->
        while (true) {
            val entry = archiveInputStream.nextEntry ?: break
            val name = entry.name
            if (!archiveInputStream.canReadEntryData(entry)) {
                log.warning("Cannot unpack entry $name, format/variant not supported")
            }
            val path = unpackDirectory.resolve(Paths.get(name))
            if (!path.startsWith(unpackDirectory)) {
                throw IOException("Cannot unpack entry $name, resulting path $path is not in the unpack directory $unpackDirectory")
            }
            if (entry.isDirectory) {
                Files.createDirectories(path)
            } else {
                createParentDirectories(path)
                Files.newOutputStream(path).buffered().use {
                    archiveInputStream.copyTo(it)
                    it.flush()
                }
            }
            val mode = when (entry) {
                is ZipArchiveEntry -> entry.unixMode
                is TarArchiveEntry -> entry.mode
                else -> -1
            }
            if (mode != -1 && mode != 0) {
                val perms = HashSet<PosixFilePermission>()
                if ((mode and 1) != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE)
                if ((mode and 2) != 0) perms.add(PosixFilePermission.OTHERS_WRITE)
                if ((mode and 4) != 0) perms.add(PosixFilePermission.OTHERS_READ)
                if ((mode and 8) != 0) perms.add(PosixFilePermission.GROUP_EXECUTE)
                if ((mode and 16) != 0) perms.add(PosixFilePermission.GROUP_WRITE)
                if ((mode and 32) != 0) perms.add(PosixFilePermission.GROUP_READ)
                if ((mode and 64) != 0) perms.add(PosixFilePermission.OWNER_EXECUTE)
                if ((mode and 128) != 0) perms.add(PosixFilePermission.OWNER_WRITE)
                if ((mode and 256) != 0) perms.add(PosixFilePermission.OWNER_READ)
                try {
                    Files.setPosixFilePermissions(path, perms)
                } catch (e: java.lang.UnsupportedOperationException) {
                    // Ignore.
                }
            }
        }
    }
}

private fun unarchiveDmgApps(dmgFile: Path, unpackDirectory: Path) {
    Files.createDirectories(unpackDirectory)
    val commands = arrayOf(
        "/usr/bin/env",
        "sh",
        "-c",
        "hdiutil attach '${dmgFile.toAbsolutePath()}' -readonly -mount required -mountpoint /Volumes/UnpackDMGFile -nobrowse && cp -a /Volumes/UnpackDMGFile/*.app '${unpackDirectory.toAbsolutePath()}/' && hdiutil detach /Volumes/UnpackDMGFile"
    )
    ProcessBuilder()
        .command(*commands)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}

/**
 * Packs [directory] into JAR file [jarFile].
 */
fun packJar(directory: Path, jarFile: Path) {
    val manifestFile = directory.resolve("META-INF/MANIFEST.MF")
    val manifest = if (Files.exists(manifestFile) && Files.isRegularFile(manifestFile)) {
        Files.newInputStream(manifestFile).buffered().use {
            Manifest(it)
        }
    } else {
        val manifest = Manifest()
        manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        manifest
    }
    createParentDirectories(jarFile)
    Files.newOutputStream(jarFile).buffered().use { outputStream ->
        JarOutputStream(outputStream, manifest).use { jarOutputStream ->
            val paths = Files.walk(directory)
            for (path in paths) {
                if (path == directory) {
                    // Skip the root directory, as it would be stored as '/' in the JAR (Zip) file, which is not allowed.
                    continue
                }
                // Files.walk returns absolute paths, so we need to relativize them here.
                val relativePath = directory.relativize(path)
                if (relativePath == Paths.get("META-INF", "MANIFEST.MF")) {
                    // Skip the 'META-INF/MANIFEST.MF' file, since this is added by passing the manifest into JarOutputStream's
                    // constructor. Adding this file again here would create a duplicate file, which results in an exception.
                    continue
                }
                val isDirectory = Files.isDirectory(path)
                val name = run {
                    // JAR (Zip) files are required to use '/' as a path separator.
                    var name = relativePath.toString().replace("\\", "/")
                    if (isDirectory && !name.endsWith("/")) {
                        // JAR (Zip) files require directories to end with '/'.
                        name = "$name/"
                    }
                    name
                }
                val entry = JarEntry(name)
                entry.time = Files.getLastModifiedTime(path).toMillis()
                jarOutputStream.putNextEntry(entry)
                when {
                    isDirectory -> {
                        jarOutputStream.closeEntry()
                    }

                    else -> {
                        Files.newInputStream(path).use { inputStream ->
                            inputStream.copyTo(jarOutputStream)
                        }
                        jarOutputStream.closeEntry()
                    }
                }
            }
            // Manually close paths stream to free up OS resources.
            paths.close()
            jarOutputStream.flush()
        }
        outputStream.flush()
    }
}
