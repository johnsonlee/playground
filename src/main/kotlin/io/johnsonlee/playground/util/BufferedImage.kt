package io.johnsonlee.playground.util

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO

fun BufferedImage.toBase64(format: String): String {
    val baos = ByteArrayOutputStream().apply {
        bufferedWriter(StandardCharsets.UTF_8).append("data:image/$format;base64,").flush()
    }
    Base64.getEncoder().wrap(baos).use { encoder ->
        ImageIO.write(this, format, encoder)
    }
    return baos.toString(StandardCharsets.UTF_8)
}