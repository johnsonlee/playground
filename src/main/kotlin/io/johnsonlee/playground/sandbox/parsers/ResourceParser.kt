package io.johnsonlee.playground.sandbox.parsers

import com.android.SdkConstants
import org.kxml2.io.KXmlParser
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

class ResourceParser(input: InputStream) : KXmlParser() {

    init {
        setFeature(FEATURE_PROCESS_NAMESPACES, true)
        setInput(input, null)
        require(START_DOCUMENT, null, null)
        next()
    }

    fun createTagSnapshot(): TagSnapshot {
        require(START_TAG, null, null)

        val tagName = name
        val tagNamespace = namespace
        val prefix = prefix
        val attributes = createAttributesForTag()
        val children = mutableListOf<TagSnapshot>()

        var hasDeclaredAaptAttrs = false
        var last: TagSnapshot? = null

        while (eventType != END_DOCUMENT) {
            when (next()) {
                START_TAG -> {
                    if (SdkConstants.AAPT_URI == namespace) {
                        if (SdkConstants.TAG_ATTR == name) {
                            val attr = createAttrTagSnapshot()
                            if (attr != null) {
                                attributes += attr
                                hasDeclaredAaptAttrs = true
                            }
                        }
                    } else {
                        val child = createTagSnapshot()
                        hasDeclaredAaptAttrs = hasDeclaredAaptAttrs || child.hasDeclaredAaptAttrs
                        children += child
                        if (last != null) {
                            last.next = child
                        }
                        last = child
                    }
                }
                END_TAG -> {
                    return TagSnapshot(tagName, tagNamespace, prefix, attributes, children.toList(), hasDeclaredAaptAttrs)
                }
            }
        }

        throw IllegalStateException("Unexpected end of document")
    }

    private fun createAttrTagSnapshot(): AaptAttrSnapshot? {
        require(START_TAG, null, "attr")

        val name = getAttributeValue(null, "name") ?: return null
        val prefix = findPrefixByQualifiedName(name)
        val namespace = getNamespace(prefix)
        val localName = findLocalNameByQualifiedName(name)
        val id = nextId.incrementAndGet().toString()
        var bundleTagSnapshot: TagSnapshot? = null

        while (eventType != END_TAG) {
            when (nextTag()) {
                START_TAG -> {
                    bundleTagSnapshot = createTagSnapshot()
                }
                END_TAG -> {
                    break
                }
            }
        }

        return if (bundleTagSnapshot != null) {
            nextTag()
            require(END_TAG, null, "attr")
            AaptAttrSnapshot(namespace, prefix, localName, id, bundleTagSnapshot)
        } else null
    }

    private fun findPrefixByQualifiedName(qualifiedName: String): String {
        return qualifiedName.substringBefore(':').takeIf(String::isNotEmpty) ?: ""
    }

    private fun findLocalNameByQualifiedName(qualifiedName: String): String {
        return qualifiedName.substringAfterLast(':')
    }

    private fun createAttributesForTag(): MutableList<AttributeSnapshot> {
        return buildList {
            (0 until attributeCount).forEach { i ->
                add(
                    AttributeSnapshot(
                        getAttributeNamespace(i),
                        getAttributePrefix(i),
                        getAttributeName(i),
                        getAttributeValue(i)
                    )
                )
            }
        }.toMutableList()
    }

    companion object {
        private val nextId = AtomicInteger(0)
    }
}