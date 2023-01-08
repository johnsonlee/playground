package io.johnsonlee.playground.util

import com.android.ide.common.rendering.api.Result

fun Result.check(): Result = apply {
    if (!isSuccess) {
        throw exception ?: Exception(errorMessage ?: status.toString())
    }
}