package io.johnsonlee.playground.sandbox

import android.content.res.Configuration
import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.resources.configuration.CountryCodeQualifier
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NetworkCodeQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.ide.common.resources.configuration.ScreenRatioQualifier
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.ide.common.resources.configuration.VersionQualifier
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.LayoutDirection
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.util.Properties

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
enum class DeviceModel(
    val screenHeight: Int,
    val screenWidth: Int,
    val xdpi: Int,
    val ydpi: Int,
    val released: String,
    val orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
    val uiMode: UiMode = UiMode.NORMAL,
    val nightMode: NightMode = NightMode.NOTNIGHT,
    val density: Density = Density.XHIGH,
    val fontScale: Float = 1.0f,
    val layoutDirection: LayoutDirection = LayoutDirection.LTR,
    val locale: String? = null,
    val ratio: ScreenRatio = ScreenRatio.NOTLONG,
    val size: ScreenSize = ScreenSize.NORMAL,
    val keyboard: Keyboard = Keyboard.NOKEY,
    val touchScreen: TouchScreen = TouchScreen.FINGER,
    val keyboardState: KeyboardState = KeyboardState.SOFT,
    val softButtons: Boolean = true,
    val navigation: Navigation = Navigation.NONAV,
    val screenRound: ScreenRound = ScreenRound.NOTROUND
) {

    PIXEL_5(
        screenHeight = 2340,
        screenWidth = 1080,
        xdpi = 442,
        ydpi = 444,
        density = Density.DPI_440,
        ratio = ScreenRatio.LONG,
        released = "October 15, 2020"
    )

    ;

    val resourceQualifiers = resourceConfiguration.qualifiers.sorted()
        .map(ResourceQualifier::getFolderSegment)
        .filterNot(String::isNullOrBlank)

    @get:JsonIgnore
    val hardwareConfig: HardwareConfig
        get() = HardwareConfig(
            screenWidth,
            screenHeight,
            density,
            xdpi.toFloat(),
            ydpi.toFloat(),
            size,
            orientation,
            screenRound,
            softButtons
        )

    @get:JsonIgnore
    val uiModeMask: Int
        get() {
            val nightMask = if (nightMode == NightMode.NIGHT) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            val typeMask = when (uiMode) {
                UiMode.NORMAL -> Configuration.UI_MODE_TYPE_NORMAL
                UiMode.DESK -> Configuration.UI_MODE_TYPE_DESK
                UiMode.CAR -> Configuration.UI_MODE_TYPE_CAR
                UiMode.TELEVISION -> Configuration.UI_MODE_TYPE_TELEVISION
                UiMode.APPLIANCE -> Configuration.UI_MODE_TYPE_APPLIANCE
                UiMode.WATCH -> Configuration.UI_MODE_TYPE_WATCH
                UiMode.VR_HEADSET -> Configuration.UI_MODE_TYPE_VR_HEADSET
            }
            return nightMask or typeMask
        }

    companion object {
        fun loadProperties(path: File): Map<String, String> {
            val p = Properties()
            path.inputStream().use(p::load)
            return p.stringPropertyNames().associateWith(p::getProperty)
        }

        private const val TAG_ATTR = "attr"
        private const val TAG_ENUM = "enum"
        private const val TAG_FLAG = "flag"
        private const val ATTR_NAME = "name"
        private const val ATTR_VALUE = "value"

        fun getEnumMap(path: File): Map<String, Map<String, Int>> {
            val map = mutableMapOf<String, MutableMap<String, Int>>()
            val xmlPullParser = XmlPullParserFactory.newInstance().newPullParser().apply {
                setInput(FileInputStream(path), null)
            }
            var eventType = xmlPullParser.eventType
            var attr: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (TAG_ATTR == xmlPullParser.name) {
                            attr = xmlPullParser.getAttributeValue(null, ATTR_NAME)
                        } else if (TAG_ENUM == xmlPullParser.name || TAG_FLAG == xmlPullParser.name) {
                            val name = xmlPullParser.getAttributeValue(null, ATTR_NAME)
                            val value = xmlPullParser.getAttributeValue(null, ATTR_VALUE)
                            val i = (java.lang.Long.decode(value) as Long).toInt()

                            require(attr != null)
                            map.getOrPut(attr) { mutableMapOf() }
                            var attributeMap: MutableMap<String, Int>? = map[attr]
                            if (attributeMap == null) {
                                attributeMap = hashMapOf()
                                map[attr] = attributeMap
                            }
                            attributeMap[name] = i
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (TAG_ATTR == xmlPullParser.name) {
                            attr = null
                        }
                    }
                }
                eventType = xmlPullParser.next()
            }

            return map
        }
    }
}

val DeviceModel.resourceConfiguration: FolderConfiguration
    get() = FolderConfiguration.createDefault().apply {
        densityQualifier = DensityQualifier(density)
        navigationMethodQualifier = NavigationMethodQualifier(navigation)
        screenDimensionQualifier = when {
            screenWidth > screenHeight -> ScreenDimensionQualifier(screenWidth, screenHeight)
            else -> ScreenDimensionQualifier(screenHeight, screenWidth)
        }
        screenRatioQualifier = ScreenRatioQualifier(ratio)
        screenSizeQualifier = ScreenSizeQualifier(size)
        textInputMethodQualifier = TextInputMethodQualifier(keyboard)
        touchTypeQualifier = TouchScreenQualifier(touchScreen)
        keyboardStateQualifier = KeyboardStateQualifier(keyboardState)
        screenOrientationQualifier = ScreenOrientationQualifier(orientation)
        screenRoundQualifier = ScreenRoundQualifier(screenRound)
        updateScreenWidthAndHeight()
        uiModeQualifier = UiModeQualifier(uiMode)
        nightModeQualifier = NightModeQualifier(nightMode)
        countryCodeQualifier = CountryCodeQualifier()
        layoutDirectionQualifier = LayoutDirectionQualifier(layoutDirection)
        networkCodeQualifier = NetworkCodeQualifier()
        localeQualifier = locale?.let(LocaleQualifier::getQualifier) ?: LocaleQualifier()
        versionQualifier = VersionQualifier()
    }