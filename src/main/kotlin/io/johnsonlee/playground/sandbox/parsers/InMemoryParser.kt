package io.johnsonlee.playground.sandbox.parsers

import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException

abstract class InMemoryParser : KXmlParser() {

    abstract fun rootTag(): TagSnapshot

    private val nodeStack = mutableListOf<TagSnapshot>()
    private var parsingState = START_DOCUMENT

    override fun getAttributeCount(): Int {
        return currentNode?.attributes?.size ?: 0
    }

    override fun getAttributeName(index: Int): String? {
        return currentNode?.attributes?.get(index)?.name
    }

    override fun getAttributeNamespace(index: Int): String {
        return getAttribute(index)?.namespace ?: ""
    }

    override fun getAttributePrefix(index: Int): String? {
        return getAttribute(index)?.prefix
    }

    override fun getAttributeValue(index: Int): String? {
        return getAttribute(index)?.value
    }

    override fun getAttributeValue(namespace: String?, name: String?): String? {
        return currentNode?.attributes?.find { it.namespace == namespace && it.name == name }?.value
    }

    override fun getDepth(): Int = nodeStack.size

    override fun getName(): String? {
        if (parsingState == START_TAG || parsingState == END_TAG) {
            return currentNode!!.name
        }
        return null
    }

    override fun next(): Int {
        when (parsingState) {
            END_DOCUMENT -> throw XmlPullParserException("Nothing after the end")
            START_DOCUMENT -> onNextFromStartDocument()
            START_TAG -> onNextFromStartTag()
            END_TAG -> onNextFromEndTag()
        }
        return parsingState
    }

    private fun push(node: TagSnapshot) {
        nodeStack.add(node)
    }

    private fun pop(): TagSnapshot {
        return nodeStack.removeLast()
    }

    private fun onNextFromStartDocument() {
        val rootTag = rootTag()
        parsingState = if (rootTag != null) {
            push(rootTag)
            START_TAG
        } else {
            END_DOCUMENT
        }
    }

    private fun onNextFromStartTag() {
        val node = currentNode!!
        val children = node.children
        parsingState = if (children.isNotEmpty()) {
            push(children[0])
            START_TAG
        } else {
            if (parsingState == START_DOCUMENT) {
                END_DOCUMENT
            } else {
                END_TAG
            }
        }
    }

    private fun onNextFromEndTag() {
        var node = currentNode!!
        val sibling = node.next
        if (sibling != null) {
            node = sibling
            pop()
            push(node)
            parsingState = START_TAG
        } else {
            pop()
            parsingState = if (nodeStack.isEmpty()) {
                END_DOCUMENT
            } else {
                END_TAG
            }
        }
    }

    private fun getAttribute(index: Int): AttributeSnapshot? {
        if (parsingState != START_TAG) {
            throw IndexOutOfBoundsException()
        }

        return currentNode?.attributes?.get(index)
    }

    private val currentNode: TagSnapshot?
        get() = nodeStack.lastOrNull()

}