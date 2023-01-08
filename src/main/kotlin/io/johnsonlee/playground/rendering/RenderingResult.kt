package io.johnsonlee.playground.rendering

import java.awt.image.BufferedImage

data class RenderingResult(
    /**
     * The rendered image
     */
    val image: BufferedImage,
    /**
     * The view tree
     */
    val view: RenderingNode
)

data class RenderingNode(
    /**
     * The view class
     */
    val `class`: String,
    /**
     * The view id
     */
    val id: String? = null,
    /**
     * The view bounds
     */
    val bounds: Bounds,
    /**
     * The view children
     */
    val children: List<RenderingNode>
)

data class Bounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
