package io.johnsonlee.playground.sandbox

import com.android.AndroidXConstants
import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import io.johnsonlee.playground.sandbox.parsers.LayoutPullParser
import io.johnsonlee.playground.sandbox.parsers.TagSnapshot
import org.kxml2.io.KXmlParser
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.lang.reflect.Modifier

class LayoutlibCallbackImpl(
    private val sandbox: Sandbox
) : LayoutlibCallback() {

    private val logger = LoggerFactory.getLogger(LayoutlibCallbackImpl::class.java)

    private val projectResources = mutableMapOf<Int, ResourceReference>()
    private val resources = mutableMapOf<ResourceReference, Int>()
    private val actionBarCallback = ActionBarCallback()
    private val aaptDeclaredResources = mutableMapOf<String, TagSnapshot>()
    private val dynamicResourceIdManager = DynamicResourceIdManager()
    private val loadedClasses = mutableMapOf<String, Class<*>>()

    init {
        initResources()
    }

    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser {
        return createXmlParserForFile(fileName)
    }

    override fun createXmlParserForFile(fileName: String): XmlPullParser {
        return FileInputStream(fileName).use { input ->
            val out = ByteArrayOutputStream()
            input.copyTo(out)
            KXmlParser().apply {
                setInput(out.toByteArray().inputStream(), null)
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            }
        }
    }

    override fun createXmlParser(): XmlPullParser = KXmlParser()

    override fun getApplicationId(): String = sandbox.configuration.applicationId

    override fun loadView(
        name: String,
        constructorSignature: Array<out Class<*>>,
        constructorArgs: Array<out Any>
    ): Any? = createNewInstance(name, constructorSignature, constructorArgs)

    override fun loadClass(
        name: String,
        constructorSignature: Array<out Class<Any>>?,
        constructorArgs: Array<out Any>?
    ): Any? = try {
        when (name) {
            AndroidXConstants.CLASS_RECYCLER_VIEW_ADAPTER.newName() -> createNewInstance(
                CLASS_ANDROIDX_CUSTOM_ADAPTER, EMPTY_CLASS_ARRAY, EMPTY_OBJECT_ARRAY
            )

            else -> super.loadClass(name, constructorSignature, constructorArgs)
        }
    } catch (e: Throwable) {
        logger.error("Failed to load class $name", e)
        null
    }

    override fun resolveResourceId(id: Int): ResourceReference? {
        return projectResources[id] ?: dynamicResourceIdManager.findById(id)
    }

    override fun getOrGenerateResourceId(resource: ResourceReference): Int {
        val ref = if (resource.resourceType == ResourceType.STYLE) {
            resource.transformStyleResource()
        } else resource
        return resources[ref] ?: dynamicResourceIdManager.getOrGenerateId(ref)
    }

    override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? {
        try {
            val value = layoutResource.value ?: return null
            if (aaptDeclaredResources.isNotEmpty() && layoutResource.resourceType == ResourceType.AAPT) {
                val aaptResource = aaptDeclaredResources.getValue(value)
                return LayoutPullParser.createFromAaptResource(aaptResource)
            }
            return LayoutPullParser.createFromFile(File(layoutResource.value)).also {
                aaptDeclaredResources += it.getAaptDeclaredAttrs()
            }
        } catch (e: FileNotFoundException) {
            return null
        }
    }

    override fun getAdapterBinding(viewObject: Any?, attributes: MutableMap<String, String>?): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = actionBarCallback

    private fun initResources() {
        for (packageName in sandbox.configuration.resourcePackageNames) {
            val rClass = try {
                Class.forName("$packageName.R")
            } catch (e: ClassNotFoundException) {
                logger.error("class ${packageName}.R not found")
                continue
            }

            for (resourceClass in rClass.declaredClasses) {
                val resourceType = ResourceType.fromClassName(resourceClass.simpleName) ?: continue
                for (field in resourceClass.declaredFields) {
                    if (!Modifier.isStatic(field.modifiers)) continue

                    val type = field.type

                    try {
                        if (type == Int::class.javaPrimitiveType) {
                            val value = field.get(null) as Int
                            val reference = ResourceReference(ResourceNamespace.RES_AUTO, resourceType, field.name)
                            projectResources[value] = reference
                            resources[reference] = value
                        } else if (type.isArray && type.componentType == Int::class.javaPrimitiveType) {
                            // ignore
                        } else {
                            logger.error("Unknown resource type (${type}): ${resourceClass.canonicalName}.${field.name}")
                        }
                    } catch (e: Throwable) {
                        logger.error("Malformed R class: ${packageName}.R")
                    }
                }
            }
        }
    }

    private fun createNewInstance(
        name: String,
        constructorSignature: Array<out Class<*>>,
        constructorArgs: Array<out Any>
    ): Any? {
        val clazz = Class.forName(name)
        val constructor = clazz.getConstructor(*constructorSignature)
        constructor.isAccessible = true
        return constructor.newInstance(*constructorArgs)
    }

    private fun ResourceReference.transformStyleResource(): ResourceReference {
        return ResourceReference.style(namespace, name.replace('.', '_'))
    }

}

private val EMPTY_CLASS_ARRAY = emptyArray<Class<*>>()

private val EMPTY_OBJECT_ARRAY = emptyArray<Any>()

private const val CLASS_ANDROIDX_CUSTOM_ADAPTER = "com.android.layoutlib.bridge.android.androidx.Adapter"
