package io.johnsonlee.playground.sandbox

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler_Delegate
import android.os.SystemClock_Delegate
import android.os._Original_Build
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.BridgeInflater
import android.view.Choreographer
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.android.internal.lang.System_Delegate
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.android.BridgeContext
import com.android.layoutlib.bridge.android.RenderParamsFlags
import com.android.layoutlib.bridge.impl.RenderAction
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.resources.aar.AarSourceResourceRepository
import com.android.resources.aar.FrameworkResourceRepository
import io.johnsonlee.playground.sandbox.parsers.LayoutPullParser
import io.johnsonlee.playground.sandbox.resources.AppResourceRepository
import io.johnsonlee.playground.util.check
import io.johnsonlee.playground.util.getFieldReflectively
import io.johnsonlee.playground.util.setStaticValue
import okio.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

class Sandbox(val configuration: Configuration) : Closeable {

    data class Configuration(
        val appDir: String = System.getProperty("user.dir"),
        val applicationId: String = "io.johnsonlee.playground",
        val resourcePackageNames: List<String> = emptyList(),
        val localResourceDirs: List<String> = emptyList(),
        val moduleResourceDirs: List<String> = emptyList(),
        val libraryResourceDirs: List<String> = emptyList(),
        val allModuleAssetDirs: List<String> = emptyList(),
        val libraryAssetDirs: List<String> = emptyList(),
        val androidHome: String = Sandbox.androidHome,
        val compileSdkVersion: Int = 31,
        val platformDir: String = File(androidHome).resolve("platforms").resolve("android-${compileSdkVersion}").path,
    ) {
        val resDir: String
        val assetsDir: String

        init {
            val app = File(appDir)
            this.resDir = app.resolve("res").path
            this.assetsDir = app.resolve("assets").path

            val platformDirPath = Path.of(platformDir)
            if (!platformDirPath.exists()) {
                val elements = platformDirPath.nameCount
                val platform = platformDirPath.subpath(elements - 1, elements)
                val platformVersion = platform.toString().split('-').last()
                throw FileNotFoundException("Missing platform version ${platformVersion}. Install with sdkmanager --install \"platforms;android-${platformVersion}\"")
            }
        }
    }

    val context: BridgeContext
        get() = RenderAction.getCurrentContext()

    val resources: Resources
        get() = context.resources

    val inflater: LayoutInflater
        get() = context.getSystemService("layout_inflater") as BridgeInflater

    internal val layoutlibCallback = LayoutlibCallbackImpl(this)
    internal val platformDataResDir = File("${configuration.platformDir}/data/res")
    internal val frameworkResources = FrameworkResourceRepository.create(
        platformDataResDir.toPath(), emptySet(), null, false
    )
    internal val projectResources = AppResourceRepository.create(
        localResourceDirectories = configuration.localResourceDirs.map(::File),
        moduleResourceDirectories = configuration.moduleResourceDirs.map(::File),
        libraryRepositories = configuration.libraryResourceDirs.map { dir ->
            val resourceDirPath = Paths.get(dir)
            AarSourceResourceRepository.create(resourceDirPath, resourceDirPath.parent.toFile().name)
        }
    )
    internal val sessionParamsBuilder = SessionParamsBuilder(
        layoutlibCallback = layoutlibCallback,
        frameworkResources = frameworkResources,
        projectResources = projectResources,
        assetRepository = SandboxAssetRepository(
            assetPath = configuration.assetsDir,
            assetDirs = configuration.allModuleAssetDirs + configuration.libraryAssetDirs
        ),
        supportsRtl = true
    ).plusFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true)
        .withTheme(DEFAULT_THEME)

    private val bridge: Bridge = run {
        val platformDataRoot = File(System.getProperty("user.dir"))
        val platformDataDir = platformDataRoot.resolve("data")
        val fontLocation = platformDataDir.resolve("fonts")
        val nativeLibLocation = platformDataDir.resolve(Sandbox.nativeLibDir)
        val icuLocation = platformDataDir.resolve("icu").resolve("icudt70l.dat")
        val keyboardLocation = platformDataDir.resolve("keyboards").resolve("Generic.kcm")
        val buildProp = File(configuration.platformDir).resolve("build.prop")
        val attrs = platformDataResDir.resolve("values").resolve("attrs.xml")
        val systemProperties = DeviceModel.loadProperties(buildProp) + mapOf(
            "debug.choreographer.frametime" to "false",
        )

        Bridge().apply {
            check(
                init(
                    systemProperties,
                    fontLocation,
                    nativeLibLocation.path,
                    icuLocation.path,
                    arrayOf(keyboardLocation.path),
                    DeviceModel.getEnumMap(attrs),
                    LayoutLogger
                )
            )

            configureBuildProperties()
            forcePlatformSdkVersion(configuration.compileSdkVersion)

            Bridge.getLock().withLock {
                Bridge.setLog(LayoutLogger)
            }

        }
    }

    fun newRenderSession(
        deviceModel: DeviceModel,
        theme: String = DEFAULT_THEME,
    ): RenderSessionImpl {
        val params = sessionParamsBuilder
            .copy(
                layoutPullParser = layoutRoot,
                deviceModel = deviceModel,
            ).withTheme(theme).build()
        val renderSession = RenderSessionImpl(params).apply {
            setElapsedFrameTimeNanos(0L)
        }
        RenderSessionImpl::class.java.getFieldReflectively("mFirstFrameExecuted").set(renderSession, true)
        Bridge.prepareThread()
        renderSession.init(params.timeout).check()
        Bitmap.setDefaultDensity(DisplayMetrics.DENSITY_DEVICE_STABLE)
        initializeAppCompatIfPresent(inflater)
        return renderSession
    }

    fun newBridgeRenderSession(renderSession: RenderSessionImpl): BridgeRenderSession {
        val inflateResult = renderSession.inflate().check()
        val bridgeRenderSessionClass = Class.forName("com.android.layoutlib.bridge.BridgeRenderSession")

        try {
            return bridgeRenderSessionClass.getDeclaredConstructor(
                RenderSessionImpl::class.java,
                com.android.ide.common.rendering.api.Result::class.java
            ).apply {
                isAccessible = true
            }.newInstance(renderSession, inflateResult) as BridgeRenderSession
        } finally {
            System_Delegate.setBootTimeNanos(0L)
        }
    }

    fun <R> withTime(timeNanos: Long, block: () -> R): R {
        val frameNanos = TIME_OFFSET_NANOS + timeNanos

        System_Delegate.setNanosTime(frameNanos)

        val choreographer = Choreographer.getInstance()
        val mCallbacksRunning = choreographer.javaClass.getFieldReflectively("mCallbacksRunning")

        return try {
            mCallbacksRunning.setBoolean(choreographer, true)

            synchronized(context.sessionInteractiveData) {
                Handler_Delegate.executeCallbacks()
            }

            val currentTimeMs = SystemClock_Delegate.uptimeMillis()
            context.sessionInteractiveData.choreographerCallbacks.execute(currentTimeMs, Bridge.getLog())
            block()
        } finally {
            mCallbacksRunning.setBoolean(choreographer, false)
        }
    }

    fun newLifecycleOwner(): LifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }

    fun newSavedStateRegistryOwner(lifecycleOwner: LifecycleOwner): SavedStateRegistryOwner =
        object : SavedStateRegistryOwner, LifecycleOwner by lifecycleOwner {
            private val controller = SavedStateRegistryController.create(this).apply {
                performRestore(null)
            }
            override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry
        }

    override fun close() {
        bridge.dispose()
    }

    private fun configureBuildProperties() {
        val buildClass = Class.forName("android.os.Build")
        val originalBuildClass = _Original_Build::class.java

        copyFieldsValue(originalBuildClass, buildClass)

        buildClass.classes.forEach { inner ->
            val originalInnerClass = originalBuildClass.classes.single { it.simpleName == inner.simpleName }
            copyFieldsValue(originalInnerClass, inner)
        }
    }

    private fun copyFieldsValue(from: Class<*>, to: Class<*>) {
        to.fields.forEach {
            try {
                val originalField = from.getField(it.name)
                to.getFieldReflectively(it.name).setStaticValue(originalField.get(null))
            } catch (e: Throwable) {
                logger.warn("Failed to set ${to.name}.${it.name}")
            }
        }
    }

    private fun forcePlatformSdkVersion(compileSdkVersion: Int) {
        val buildVersionClass = try {
            Class.forName("android.os.Build\$VERSION")
        } catch (e: ClassNotFoundException) {
            return
        }
        buildVersionClass.getFieldReflectively("SDK_INT").setStaticValue(compileSdkVersion)
    }

    private fun initializeAppCompatIfPresent(inflater: LayoutInflater) {
        lateinit var appCompatDelegateClass: Class<*>

        try {
            Class.forName("androidx.appcompat.widget.AppCompatDrawableManager").run {
                getMethod("preload").invoke(null)
            }
            appCompatDelegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate")
        } catch (e: ClassNotFoundException) {
            logger.debug("AppCompat not found on classpath")
            return
        }

        if (inflater.factory == null) {
            inflater.factory2 = object : LayoutInflater.Factory2 {
                val appCompatViewInflaterClass = Class.forName("androidx.appcompat.app.AppCompatViewInflater")
                val createViewMethod = appCompatViewInflaterClass.getDeclaredMethod(
                    "createView",
                    View::class.java,
                    String::class.java,
                    Context::class.java,
                    AttributeSet::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }

                override fun onCreateView(parent: View?, name: String, context: Context, attrs: AttributeSet): View? {
                    return createViewMethod.invoke(
                        appCompatViewInflaterClass.getConstructor().newInstance(),
                        parent,
                        name,
                        context,
                        attrs,
                        true,
                        true,
                        true,
                        true
                    ) as? View
                }

                override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
                    return onCreateView(null, name, context, attrs)
                }

            }
        } else {
            if (!appCompatDelegateClass.isAssignableFrom(inflater.factory2.javaClass)) {
                throw IllegalStateException("The LayoutInflater already has a Factory installed so we can not install AppCompat's")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Sandbox::class.java)

        private const val DEFAULT_THEME = "Theme.AppCompat.Light.NoActionBar"

        private val TIME_OFFSET_NANOS = TimeUnit.HOURS.toNanos(1L)

        val hasViewTreeLifecycleOwner = isPresentInClasspath("androidx.lifecycle.ViewTreeLifecycleOwner")

        val hasSavedStateRegistryController =
            isPresentInClasspath("androidx.savedstate.SavedStateRegistryController\$Companion")

        internal fun isPresentInClasspath(vararg classNames: String): Boolean {
            return try {
                classNames.forEach {
                    Class.forName(it)
                }
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

}

private val layoutRoot: LayoutPullParser
    get() = LayoutPullParser.createFromString(
        """
        |<?xml version="1.0" encoding="utf-8"?>
        |<FrameLayout
        |    xmlns:android="http://schemas.android.com/apk/res/android"
        |    android:layout_width="match_parent"
        |    android:layout_height="match_parent"
        |/>
        """.trimMargin()
    )

val Sandbox.Companion.androidHome: String
    get() = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: androidSdkPath

val Sandbox.Companion.androidSdkPath: String
    get() {
        val osName = System.getProperty("os.name").lowercase()
        return if (osName.startsWith("windows")) {
            "${System.getProperty("user.home")}\\AppData\\Local\\Android\\Sdk"
        } else if (osName.startsWith("mac")) {
            "${System.getProperty("user.home")}/Library/Android/sdk"
        } else {
            "/usr/local/share/android-sdk"
        }
    }

val Sandbox.Companion.nativeLibDir: String
    get() {
        val osName = System.getProperty("os.name").lowercase()
        val osLabel = when {
            osName.startsWith("windows") -> "win"
            osName.startsWith("mac") -> {
                val arch = System.getProperty("os.arch").lowercase()
                if (arch.startsWith("x86")) "mac" else "mac-arm"
            }
            else -> "linux"
        }
        return "${osLabel}/lib64"
    }
