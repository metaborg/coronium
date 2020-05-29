package mb.coronium.util

import org.gradle.api.logging.Logger

class GradleLog(private val logger: Logger) : Log {
  override fun error(message: String) {
    logger.error(message)
  }

  override fun warning(message: String) {
    logger.warn(message)
  }

  override fun progress(message: String) {
    logger.lifecycle(message)
  }

  override fun info(message: String) {
    logger.info(message)
  }

  override fun debug(message: String) {
    logger.debug(message)
  }
}
