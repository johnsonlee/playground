package io.johnsonlee.playground.sandbox.parsers

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.ResourceNamespace
import okio.buffer
import okio.source
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class LayoutPullParser : InMemoryParser, AaptAttrParser, ILayoutPullParser {

    private val root: TagSnapshot
    private val declaredAaptAttrs: Map<String, TagSnapshot>

    private var layoutNamespace: ResourceNamespace = ResourceNamespace.RES_AUTO

    constructor(input: InputStream) {
        val buffer = input.source().buffer()
        setFeature(FEATURE_PROCESS_NAMESPACES, true)
        setInput(buffer.peek().inputStream(), null)

        val resourceParser = ResourceParser(buffer.inputStream())
        root = resourceParser.createTagSnapshot()
        declaredAaptAttrs = findDeclaredAaptAttrs(root)
    }

    constructor(aaptResource: TagSnapshot) {
        root = aaptResource
        declaredAaptAttrs = emptyMap()
    }

    override fun rootTag(): TagSnapshot = root

    override fun getViewCookie(): Any? {
        val name = super.getName() ?: return null

        return if (
            SdkConstants.LIST_VIEW == name
            || SdkConstants.EXPANDABLE_LIST_VIEW == name
            || SdkConstants.GRID_VIEW == name
            || SdkConstants.SPINNER == name
        ) {
            (0 until attributeCount).filter {
                getAttributeNamespace(it) == SdkConstants.TOOLS_URI && getAttributeName(it) != SdkConstants.ATTR_IGNORE
            }.associate {
                getAttributeName(it)!! to getAttributeValue(it)!!
            }
        } else null
    }

    override fun getLayoutNamespace(): ResourceNamespace = layoutNamespace

    override fun getAaptDeclaredAttrs(): Map<String, TagSnapshot> = declaredAaptAttrs

    fun setLayoutNamespace(namespace: ResourceNamespace) {
        this.layoutNamespace = namespace
    }

    private fun findDeclaredAaptAttrs(tag: TagSnapshot): Map<String, TagSnapshot> {
        if (!tag.hasDeclaredAaptAttrs) {
            return emptyMap()
        }

        return buildMap {
            tag.attributes.filterIsInstance<AaptAttrSnapshot>().forEach { attr ->
                val bundledTag = attr.bundledTag
                put(attr.id, bundledTag)
                for (child in bundledTag.children) {
                    putAll(findDeclaredAaptAttrs(child))
                }
            }
            for (child in tag.children) {
                putAll(findDeclaredAaptAttrs(child))
            }
        }
    }

    companion object {
        fun createFromFile(layout: File) = LayoutPullParser(FileInputStream(layout))

        fun createFromClasspath(layout: String): LayoutPullParser {
            var path = layout
            if (path.startsWith("/")) {
                path = path.substring(1)
            }
            return LayoutPullParser::class.java.classLoader.getResourceAsStream(path)?.let(::LayoutPullParser)
                ?: throw IOException("Resource not found: $layout")
        }

        fun createFromString(xml: String): LayoutPullParser {
            return LayoutPullParser(xml.byteInputStream(StandardCharsets.UTF_8))
        }

        fun createFromAaptResource(aaptResource: TagSnapshot): LayoutPullParser {
            return LayoutPullParser(aaptResource)
        }
    }

}