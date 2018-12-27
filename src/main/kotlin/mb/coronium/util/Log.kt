package mb.coronium.util

import java.io.PrintWriter

interface Log {
  fun error(message: String)
  fun warning(message: String)
  fun progress(message: String)
  fun info(message: String)
  fun debug(message: String)
}

class StreamLog(private val writer: PrintWriter = PrintWriter(System.out, true)) : Log {
  override fun error(message: String) {
    writer.println(message)
  }

  override fun warning(message: String) {
    writer.println(message)
  }

  override fun progress(message: String) {
    writer.println(message)
  }

  override fun info(message: String) {
    writer.println(message)
  }

  override fun debug(message: String) {
    writer.println(message)
  }
}