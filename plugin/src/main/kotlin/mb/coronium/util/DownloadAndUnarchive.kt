package mb.coronium.util

import mb.coronium.util.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path

fun downloadAndUnarchive(urlStr: String, cacheDir: Path, forceDownload: Boolean, log: Log): Unarchived {
  val filename = run {
    val index = urlStr.lastIndexOf('/')
    if(index == -1) {
      error("Cannot download and unarchive '$urlStr', it has no filename")
    }
    urlStr.substring(index + 1)
  }
  val unpackDir = cacheDir.resolve(filename.replace('.', '_'))
  val archiveFile = cacheDir.resolve(filename)
  val url = URL(urlStr)
  val shouldDownload = forceDownload || shouldDownload(url, archiveFile)
  if(shouldDownload) {
    log.progress("Downloading $url into $archiveFile")
    downloadFileFromUrl(url, archiveFile)
  }
  val unpackDirExists = Files.isDirectory(unpackDir)
  val shouldUnpack = shouldDownload || !unpackDirExists
  if(shouldUnpack) {
    if(unpackDirExists) {
      deleteNonEmptyDirectoryIfExists(unpackDir)
    }
    log.progress("Unpacking $archiveFile into $unpackDir")
    unarchive(archiveFile, unpackDir, log)
  }
  return Unarchived(unpackDir, shouldUnpack)
}

data class Unarchived(val unarchiveDir: Path, val wasUnarchived: Boolean)
