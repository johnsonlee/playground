package io.johnsonlee.playground.util

import com.android.resources.Density

fun create(dpi: Int): Density {
    val density = Density.getEnum(dpi)
    if (null != density) return density
    val value = "${dpi}dpi"
    return Density::class.java.getConstructor(
        String::class.java,
        String::class.java,
        Int::class.java,
        Int::class.java
    ).apply {
        isAccessible = true
    }.newInstance(value, value, dpi, 1)
}