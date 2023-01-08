package io.johnsonlee.playground.util

import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import kotlin.time.measureTime

internal object Gc {

    private val logger = LoggerFactory.getLogger(Gc::class.java)

    fun gc() {
        val duration = measureTime {
            var obj: Any? = Any()
            val ref = WeakReference<Any>(obj)

            obj = null

            while (ref.get() != null) {
                System.gc()
                System.runFinalization()
            }

            System.gc()
            System.runFinalization()
        }

        logger.info("GC completed in $duration")
    }

}