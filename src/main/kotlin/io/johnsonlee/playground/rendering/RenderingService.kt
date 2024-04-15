package io.johnsonlee.playground.rendering

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.SdkConstants
import com.android.ide.common.xml.AndroidManifestParser
import io.johnsonlee.playground.sandbox.Environment
import io.johnsonlee.playground.sandbox.RenderData
import io.johnsonlee.playground.sandbox.Sandbox
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

@Service
class RenderingService(
        @Value("\${playground.app.dir}") private val appDir: String,
        @Value("\${playground.library.dir}") private val libraryDir: String,
) {

    private val libraries = File(appDir).resolve(libraryDir).listFiles { lib ->
        lib.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML).exists()
    }?.toList() ?: emptyList()

    fun render(params: RenderingParams, onCreateView: (LayoutInflater, ViewGroup) -> View?): RenderData {
        val env = Environment(
                resourcePackageNames = libraries.map { lib ->
                    AndroidManifestParser.parse(lib.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML).toPath()).`package`
                },
                libraryResourceDirs = libraries.map { lib ->
                    lib.resolve(SdkConstants.FD_RES)
                }.filter(File::exists).map(File::getPath),
                libraryAssetDirs = libraries.map { lib ->
                    lib.resolve(SdkConstants.FD_ASSETS)
                }.filter(File::exists).map(File::getPath),
        )
        return Sandbox(env).run { context, parent ->
            onCreateView(LayoutInflater.from(context), parent)
        }.getOrThrow()
    }

}