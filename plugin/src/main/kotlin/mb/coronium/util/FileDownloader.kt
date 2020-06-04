package mb.coronium.util

import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * Downloads file at given [url] into [file].
 */
fun downloadFileFromUrl(url: URL, file: Path) {
  if(Files.isDirectory(file)) {
    throw IOException("Cannot download file (from $url) into $file, as it is a directory")
  }
  createParentDirectories(file)
  val connection = url.openConnection()
  connection.connectTimeout = 5000
  connection.readTimeout = 5000
  Files.newOutputStream(file).buffered().use { outputStream ->
    connection.getInputStream().buffered().use { inputStream ->
      inputStream.copyTo(outputStream)
    }
    outputStream.flush()
  }
}

/**
 * Checks if file at [url] should be downloaded into [file]. Returns false if files are of the same size.
 */
fun shouldDownload(url: URL, file: Path): Boolean {
  if(Files.isDirectory(file)) {
    throw IOException("Cannot check if $url should be downloaded into $file, as it is a directory")
  }
  return if(!Files.exists(file)) {
    true
  } else {
    // Skip checking if file should be re-downloaded within a day.
    val timestamp = Files.getLastModifiedTime(file).toInstant()
    if(timestamp.isAfter(Instant.now().minusSeconds(86400))) {
      return false
    }
    Files.setLastModifiedTime(file, FileTime.from(Instant.now()))

    // Check if file size has changed.
    val connection = url.openConnection()
    connection.connectTimeout = 5000
    connection.readTimeout = 5000
    val remoteContentLength = try {
      connection.getHeaderField("Content-Length")?.toLong()
    } catch(_: NumberFormatException) {
      null
    }
    remoteContentLength != null && remoteContentLength != Files.size(file)
  }
}
