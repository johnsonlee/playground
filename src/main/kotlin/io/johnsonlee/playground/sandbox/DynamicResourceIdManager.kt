package io.johnsonlee.playground.sandbox

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType

class DynamicResourceIdManager {

    private class IdProvider(private val packageByte: Byte) {
        private val counters: ShortArray = ShortArray(ResourceType.values().size) { 0xffff.toShort() }

        fun getNext(type: ResourceType): Int {
            return buildResourceId(packageByte, (type.ordinal + 1).toByte(), --counters[type.ordinal])
        }
    }

    private var nextPackageId = FIRST_PACKAGE_ID

    private val mutex = Any()
    private val perNamespaceProviders = hashMapOf<ResourceNamespace, IdProvider>()
    private val dynamicToIdMap = hashMapOf<ResourceReference, Int>()
    private val dynamicFromIdMap = hashMapOf<Int, ResourceReference>()

    init {
        perNamespaceProviders[ResourceNamespace.ANDROID] = IdProvider(0x01)
        perNamespaceProviders[ResourceNamespace.RES_AUTO] = IdProvider(0x7f)
    }

    fun findById(id: Int): ResourceReference? = dynamicFromIdMap[id]

    fun getOrGenerateId(resource: ResourceReference): Int {
        val dynamicId = dynamicToIdMap[resource]
        if (dynamicId != null) {
            return dynamicId
        }

        return synchronized(mutex) {
            val provider = perNamespaceProviders.getOrPut(resource.namespace) {
                IdProvider(nextPackageId++)
            }
            val newId = provider.getNext(resource.resourceType)
            dynamicToIdMap[resource] = newId
            dynamicFromIdMap[newId] = resource
            newId
        }
    }

}

private const val FIRST_PACKAGE_ID: Byte = 0x02

private fun buildResourceId(packageId: Byte, typeId: Byte, entryId: Short): Int {
    return (packageId.toInt() shl 24) or (typeId.toInt() shl 16) or (entryId.toInt() and 0xffff)
}