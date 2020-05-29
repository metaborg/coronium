package mb.coronium.util

import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

/**
 * Downloads file at given [url] into [file].
 */
fun downloadFileFromUrl(url: URL, file: Path) {
  if(Files.isDirectory(file)) {
    throw IOException("Cannot download file (from $url) into $file, as it is a directory")
  }
  createParentDirectories(file)
  val connection = url.openConnection()
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
  val connection = url.openConnection()
  return if(!Files.exists(file)) {
    true
  } else {
    val remoteContentLength = try {
      connection.getHeaderField("Content-Length")?.toLong()
    } catch(_: NumberFormatException) {
      null
    }
    remoteContentLength != null && remoteContentLength != Files.size(file)
    // TODO: also check last modified date?
  }
}