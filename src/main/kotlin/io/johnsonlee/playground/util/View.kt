package io.johnsonlee.playground.util

import android.content.res.Resources
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import io.johnsonlee.playground.rendering.Bounds
import io.johnsonlee.playground.rendering.RenderingNode

val View.identifier: String
    get() {
        val r = resources
        val id = id
        if (id > 0 && Resources.resourceHasPackage(id) && r != null) {
            try {
                val pkgName = when (id ushr 24) {
                    0x01 -> "android"
                    0x7f -> "app"
                    else -> r.getResourcePackageName(id)
                }
                val typeName = r.getResourceTypeName(id)
                val entryName = r.getResourceEntryName(id)
                return "${pkgName}:${typeName}/${entryName}}"
            } catch (_: Resources.NotFoundException) {
            }
        }

        return "#${Integer.toHexString(id)}"
    }

fun View.toRenderingNode(): RenderingNode {
    val bounds = Rect().apply {
        getBoundsOnScreen(this)
    }
    return RenderingNode(
        `class` = this::class.java.name,
        id = this.identifier,
        bounds = Bounds(x = bounds.left, y = bounds.top, width = bounds.width(), height = bounds.height()),
        children = when (this) {
            is ViewGroup -> (0 until childCount).map(this::getChildAt).map(View::toRenderingNode)
            else -> emptyList()
        }
    )
}
