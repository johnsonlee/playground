package io.johnsonlee.playground.sandbox

import com.android.SdkConstants
import com.android.ide.common.rendering.api.AssetRepository
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.getConfiguredResources
import com.android.layoutlib.bridge.Bridge
import com.android.resources.LayoutDirection
import com.android.resources.ResourceType
import io.johnsonlee.playground.sandbox.parsers.LayoutPullParser

data class SessionParamsBuilder(
    val layoutlibCallback: LayoutlibCallback,
    val frameworkResources: ResourceRepository,
    val assetRepository: AssetRepository,
    val projectResources: ResourceRepository,
    val deviceModel: DeviceModel = DeviceModel.PIXEL_5,
    val renderingMode: SessionParams.RenderingMode = SessionParams.RenderingMode.NORMAL,
    val targetSdkVersion: Int = 21,
    val flags: Map<SessionParams.Key<*>, Any> = emptyMap(),
    val themeName: String? = null,
    val isProjectTheme: Boolean = false,
    val layoutPullParser: LayoutPullParser? = null,
    val projectKey: Any? = null,
    val minSdkVersion: Int = 0,
    val decor: Boolean = true,
    val supportsRtl: Boolean = false
) {

    fun withTheme(
        themeName: String,
        isProjectTheme: Boolean = false
    ) = copy(themeName = themeName, isProjectTheme = isProjectTheme)

    fun withTheme(themeName: String) = when {
        themeName.startsWith(SdkConstants.PREFIX_ANDROID) -> {
            withTheme(themeName.substring(SdkConstants.PREFIX_ANDROID.length), false)
        }

        else -> withTheme(themeName, true)
    }

    fun plusFlag(flag: SessionParams.Key<*>, value: Any) = copy(flags = flags + (flag to value))

    fun build(): SessionParams {
        require(themeName != null)

        val folderConfiguration = deviceModel.resourceConfiguration
        val resourceResolver = ResourceResolver.create(
            mapOf(
                ResourceNamespace.ANDROID to frameworkResources.getConfiguredResources(folderConfiguration).row(ResourceNamespace.ANDROID),
                *projectResources.getConfiguredResources(folderConfiguration).rowMap().map { (key, value) -> key to value }.toTypedArray()
            ),
            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, themeName),
        )
        val sessionParams = SessionParams(
            layoutPullParser,
            renderingMode,
            projectKey,
            deviceModel.hardwareConfig,
            resourceResolver,
            layoutlibCallback,
            minSdkVersion,
            targetSdkVersion,
            LayoutLogger
        )

        sessionParams.fontScale = deviceModel.fontScale
        sessionParams.uiMode = deviceModel.uiModeMask

        val localeQualifier = folderConfiguration.localeQualifier
        val layoutDirectionQualifier = folderConfiguration.layoutDirectionQualifier

        if (LayoutDirection.RTL == layoutDirectionQualifier.value && !Bridge.isLocaleRtl(localeQualifier.tag)) {
            sessionParams.locale = "ur"
        } else {
            sessionParams.locale = localeQualifier.tag
        }

        sessionParams.setRtlSupport(supportsRtl)

        flags.forEach { (key, value) ->
            sessionParams.setFlag(key as SessionParams.Key<Any>, value)
        }
        sessionParams.setAssetRepository(assetRepository)
        if (!decor) {
            sessionParams.setForceNoDecor()
        }

        return sessionParams
    }

}
