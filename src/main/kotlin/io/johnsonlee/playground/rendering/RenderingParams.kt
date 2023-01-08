package io.johnsonlee.playground.rendering

import android.view.ViewGroup.LayoutParams
import com.fasterxml.jackson.databind.JsonNode
import io.johnsonlee.playground.sandbox.DeviceModel

data class RenderingParams(
    val template: String? = null,
    val data: JsonNode? = null,
    val options: RenderingOptions
)

data class RenderingOptions(
    val format: String = "png",
    val width: Int = LayoutParams.MATCH_PARENT,
    val height: Int = LayoutParams.WRAP_CONTENT,
    val pack: Boolean = false,
    val debug: Boolean = false,
    val device: String = DeviceModel.PIXEL_5.name
)
