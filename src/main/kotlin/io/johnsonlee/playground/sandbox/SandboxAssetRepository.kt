package io.johnsonlee.playground.sandbox

import com.android.ide.common.rendering.api.AssetRepository
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class SandboxAssetRepository(
    private val assetPath: String,
    private val assetDirs: List<String> = emptyList()
) : AssetRepository() {

    override fun isSupported(): Boolean = true

    override fun openAsset(path: String?, mode: Int): InputStream? = if (assetDirs.isEmpty()) {
        open("$assetPath/$path")
    } else {
        assetDirs.asSequence()
            .map { "$it/$assetPath/$path" }
            .map(::open)
            .firstOrNull { it != null }
    }

    override fun openNonAsset(cookie: Int, path: String, mode: Int): InputStream? = open(path)

    private fun open(path: String): InputStream? {
        val asset = File(path)
        return when {
            asset.isFile -> FileInputStream(asset)
            else -> null
        }
    }

}