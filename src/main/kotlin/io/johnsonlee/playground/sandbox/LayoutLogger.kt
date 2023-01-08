package io.johnsonlee.playground.sandbox

import com.android.ide.common.rendering.api.ILayoutLog
import org.slf4j.LoggerFactory

object LayoutLogger: ILayoutLog {

    private val logger = LoggerFactory.getLogger(LayoutLogger::class.java)

    override fun warning(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
        logger.warn("${tag}: ${message}")
    }

    override fun fidelityWarning(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {
        logger.warn("${tag}: ${message}", throwable)
    }

    override fun error(tag: String?, message: String?, viewCookie: Any?, data: Any?) {
        logger.error("${tag}: ${message}")
    }

    override fun error(tag: String?, message: String?, throwable: Throwable?, viewCookie: Any?, data: Any?) {
        logger.error("${tag}: ${message}", throwable)
    }

    override fun logAndroidFramework(priority: Int, tag: String?, message: String?) {
        logger.info("${tag} [${priority}]: ${message}")
    }
}