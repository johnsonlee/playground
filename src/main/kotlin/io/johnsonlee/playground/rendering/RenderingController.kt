package io.johnsonlee.playground.rendering

import com.android.layoutlib.bridge.android.BridgeContext
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser
import io.johnsonlee.playground.sandbox.RenderData
import io.johnsonlee.playground.sandbox.parsers.LayoutPullParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.time.measureTimedValue

@RestController
class RenderingController(
    @Autowired private val renderingService: RenderingService
) {

    private val logger = LoggerFactory.getLogger(RenderingController::class.java)

    @GetMapping("/api/render")
    fun render() : Any = mapOf("hello" to "world")

    @PostMapping("/api/render")
    fun render(
        @RequestBody params: RenderingParams
    ): RenderData {
        val (result, duration) = measureTimedValue {
            renderingService.render(params) { inflater, parent ->
                val parser = LayoutPullParser.createFromClasspath("main.xml")
                val layout = BridgeXmlBlockParser(parser, inflater.context as BridgeContext, parser.layoutNamespace)
                inflater.inflate(layout, parent, false).apply {
                    parent.addView(this)
                }
            }
        }

        logger.info("Rendering took $duration")

        return result
    }

}
