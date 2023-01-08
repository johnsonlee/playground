@file:Suppress("removal")
package io.johnsonlee.playground.util

import sun.misc.Unsafe
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.security.AccessController
import java.security.PrivilegedAction

fun Class<*>.getFieldReflectively(name: String): Field {
    return getDeclaredField(name).apply {
        isAccessible = true
    }
}

fun Field.setStaticValue(value: Any) {
    this.isAccessible = true

    if (Modifier.isFinal(this.modifiers)) {
        AccessController.doPrivileged<Any?>(PrivilegedAction{
            val unsafe = Unsafe::class.java.getFieldReflectively("theUnsafe").get(null) as Unsafe
            val offset = unsafe.staticFieldOffset(this)
            val base = unsafe.staticFieldBase(this)
            unsafe.setFieldValue(this, base, offset, value)
            null
        })
    } else {
        this.set(null, value)
    }
}

private fun Unsafe.setFieldValue(field: Field, base: Any, offset: Long, value: Any) = when (field.type) {
    java.lang.Integer.TYPE -> this.putInt(base, offset, value as Int)
    java.lang.Short.TYPE -> this.putShort(base, offset, value as Short)
    java.lang.Byte.TYPE -> this.putByte(base, offset, value as Byte)
    java.lang.Long.TYPE -> this.putLong(base, offset, value as Long)
    java.lang.Float.TYPE -> this.putFloat(base, offset, value as Float)
    java.lang.Double.TYPE -> this.putDouble(base, offset, value as Double)
    java.lang.Boolean.TYPE -> this.putBoolean(base, offset, value as Boolean)
    java.lang.Character.TYPE -> this.putChar(base, offset, value as Char)
    else -> this.putObject(base, offset, value)
}