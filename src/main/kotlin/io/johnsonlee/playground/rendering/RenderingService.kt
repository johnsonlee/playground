package io.johnsonlee.playground.rendering

import android.animation.AnimationHandler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.SdkConstants
import com.android.ide.common.xml.AndroidManifestParser
import com.android.layoutlib.bridge.Bridge
import io.johnsonlee.playground.sandbox.DeviceModel
import io.johnsonlee.playground.sandbox.Sandbox
import io.johnsonlee.playground.util.check
import io.johnsonlee.playground.util.toRenderingNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class RenderingService(
    @Value("\${playground.app.dir}") private val appDir: String,
    @Value("\${playground.library.dir}") private val libraryDir: String,
) {

    private val libraries = File(appDir).resolve(libraryDir).listFiles { lib ->
        lib.resolve(SdkConstants.FN_ANDROID_MANIFEST_XML).exists()
    }?.toList() ?: emptyList()

    fun render(params: RenderingParams, onCreateView: (LayoutInflater, ViewGroup) -> View?): RenderingResult {
        val configuration = Sandbox.Configuration(
            appDir = appDir,
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
        return Sandbox(configuration).use { sandbox ->
            val renderSession = sandbox.newRenderSession(
                deviceModel = try {
                    params.options.device.uppercase().let(DeviceModel::valueOf)
                } catch (e: Throwable) {
                    DeviceModel.PIXEL_5
                },
            )
            val bridgeRenderSession = sandbox.newBridgeRenderSession(renderSession)
            val rootView = bridgeRenderSession.rootViews[0].viewObject as ViewGroup

            try {
                sandbox.withTime(0L) {}

                val view = onCreateView(sandbox.inflater, rootView)?.apply {
                    isShowingLayoutBounds = params.options.debug
                }

                sandbox.withTime(TimeUnit.MILLISECONDS.toNanos(0)) {
                    renderSession.render(true).check()
                }

                RenderingResult(
                    image = bridgeRenderSession.image,
                    view = (view ?: rootView).toRenderingNode()
                )
            } finally {
                rootView.removeAllViews()
                AnimationHandler.sAnimatorHandler.set(null)
                renderSession.release()
                bridgeRenderSession.dispose()
                Bridge.cleanupThread()
            }

        }
    }

}