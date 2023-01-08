package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.resources.ResourceTable
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import com.android.resources.ResourceType
import com.android.resources.aar.AarSourceResourceRepository
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Maps
import com.google.common.collect.Multimap
import com.google.common.collect.Multiset
import com.google.common.collect.Table
import com.google.common.collect.Tables

/**
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:render-resources/src/com/android/tools/res/MultiResourceRepository.java
 */
abstract class MultiResourceRepository protected constructor(
    displayName: String
) : LocalResourceRepository(displayName) {

    private var localResources = listOf<LocalResourceRepository>()

    private var libraryResources = listOf<AarSourceResourceRepository>()

    private var children = listOf<ResourceRepository>()

    private var leafsByNamespace = ImmutableListMultimap.of<ResourceNamespace, SingleNamespaceResourceRepository>()

    private var repositoriesByNamespace =
        ImmutableListMultimap.of<ResourceNamespace, SingleNamespaceResourceRepository>()

    private var resourceComparator = ResourceItemComparator(ResourcePriorityComparator(emptyList()))

    private val cachedMaps = ResourceTable()

    private val resourceNames: Table<SingleNamespaceResourceRepository, ResourceType, Set<String>> =
        Tables.newCustomTable(HashMap()) {
            Maps.newEnumMap(ResourceType::class.java)
        }

    fun setChildren(
        localResources: List<LocalResourceRepository>,
        libraryResources: Collection<AarSourceResourceRepository>
    ) {
        this.localResources = localResources.toList()
        this.libraryResources = libraryResources.toList()
        this.children = this.localResources + this.libraryResources
        this.leafsByNamespace = ImmutableListMultimap.builder<ResourceNamespace, SingleNamespaceResourceRepository>()
            .computeLeafs(this)
            .build()
        this.repositoriesByNamespace =
            ImmutableListMultimap.builder<ResourceNamespace, SingleNamespaceResourceRepository>()
                .computeNamespaceMap(this)
                .build()
        this.resourceComparator = ResourceItemComparator(ResourcePriorityComparator(this.leafsByNamespace.values()))
        this.cachedMaps.clear()
    }

    override fun getNamespaces(): Set<ResourceNamespace> = repositoriesByNamespace.keySet()

    override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
        for (namespace in namespaces) {
            if (visitor.shouldVisitNamespace(namespace)) {
                for (type in ResourceType.values()) {
                    if (visitor.shouldVisitResourceType(type)) {
                        val map = getMap(namespace, type)
                        if (map != null) {
                            for (item in map.values()) {
                                if (visitor.visit(item) == ResourceVisitor.VisitResult.ABORT) {
                                    return ResourceVisitor.VisitResult.ABORT
                                }
                            }
                        }
                    }
                }
            }
        }
        return ResourceVisitor.VisitResult.CONTINUE
    }

    override fun getMap(namespace: ResourceNamespace, resourceType: ResourceType): ListMultimap<String, ResourceItem>? {
        val repositoriesForNamespace = leafsByNamespace[namespace]
        if (repositoriesForNamespace.size == 1) {
            val repository = repositoriesForNamespace[0]
            return getResources(repository, namespace, resourceType)
        }

        val cachedMap = cachedMaps[namespace, resourceType]
        if (null != cachedMap) {
            return cachedMap
        }

        val map = if (resourceType == ResourceType.STYLEABLE || resourceType == ResourceType.ID) {
            ArrayListMultimap.create<String, ResourceItem>()
        } else {
            PerConfigResourceMap(resourceComparator)
        }

        cachedMaps.put(namespace, resourceType, map)

        for (repository in repositoriesForNamespace) {
            val items = getResources(repository, namespace, resourceType)
            if (items.isEmpty) continue
            map.putAll(items)
            if (repository is LocalResourceRepository) {
                resourceNames.put(repository, resourceType, items.keySet().toSet())
            }
        }

        return map
    }

    override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
        return leafsByNamespace.values()
    }

    companion object {
        private fun ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository>.computeLeafs(
            repository: ResourceRepository
        ): ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> = apply {
            when (repository) {
                is MultiResourceRepository -> {
                    repository.children.forEach {
                        computeLeafs(it)
                    }
                }

                else -> {
                    repository.leafResourceRepositories.forEach {
                        this.put(it.namespace, it)
                    }
                }
            }
        }

        private fun ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository>.computeNamespaceMap(
            repository: ResourceRepository
        ): ImmutableListMultimap.Builder<ResourceNamespace, SingleNamespaceResourceRepository> = apply {
            when (repository) {
                is MultiResourceRepository -> {
                    repository.children.forEach {
                        computeNamespaceMap(it)
                    }
                }

                is SingleNamespaceResourceRepository -> {
                    this.put(repository.namespace, repository)
                }
            }
        }

        private fun getResources(
            repository: SingleNamespaceResourceRepository,
            namespace: ResourceNamespace,
            type: ResourceType
        ): ListMultimap<String, ResourceItem> = when (repository) {
            is LocalResourceRepository -> {
                repository.getMapPackageAccessible(namespace, type) ?: ImmutableListMultimap.of()
            }

            else -> repository.getResources(namespace, type)
        }
    }

    private class ResourceItemComparator(
        val priorityComparator: Comparator<ResourceItem>
    ) : Comparator<ResourceItem> {
        override fun compare(l: ResourceItem, r: ResourceItem): Int {
            return l.configuration.compareTo(r.configuration).takeIf {
                it != 0
            } ?: priorityComparator.compare(l, r)
        }
    }

    private class ResourcePriorityComparator(
        repositories: Collection<SingleNamespaceResourceRepository>
    ) : Comparator<ResourceItem> {

        private val repositoryOrdering = buildMap {
            repositories.forEachIndexed { index, repository ->
                put(repository, index)
            }
        }

        override fun compare(l: ResourceItem, r: ResourceItem): Int {
            return getOrdering(l).compareTo(getOrdering(r))
        }

        private fun getOrdering(item: ResourceItem): Int {
            val repository = item.repository
            return repositoryOrdering[repository] ?: 0
        }
    }

    private class PerConfigResourceMap(
        private val comparator: ResourceItemComparator
    ) : ListMultimap<String, ResourceItem> {
        private val map = HashMap<String, MutableList<ResourceItem>>()

        private var size = 0

        private var values: Values? = null

        override fun get(key: String?): List<ResourceItem> {
            return key?.let(map::get) ?: emptyList()
        }

        override fun keySet(): MutableSet<String> = map.keys

        override fun keys(): Multiset<String> = throw UnsupportedOperationException()

        override fun values(): Collection<ResourceItem> {
            var values = this.values
            if (values == null) {
                values = Values(size)
                this.values = values
            }

            return values
        }

        override fun entries(): Collection<MutableMap.MutableEntry<String, ResourceItem>> {
            throw UnsupportedOperationException()
        }

        override fun removeAll(key: Any?): List<ResourceItem> {
            val removed = key?.let(map::remove)
            if (null != removed) {
                size -= removed.size
            }
            return removed ?: emptyList()
        }

        override fun clear() {
            map.clear()
            size = 0
        }

        override fun size(): Int = size

        override fun isEmpty(): Boolean = size == 0

        override fun containsKey(key: Any?): Boolean = key?.let(map::containsKey) ?: false

        override fun containsValue(value: Any?): Boolean {
            throw UnsupportedOperationException()
        }

        override fun containsEntry(key: Any?, value: Any?): Boolean {
            throw UnsupportedOperationException()
        }

        override fun put(key: String, value: ResourceItem): Boolean {
            val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
            val oldSize = list.size
            list += value
            size += list.size - oldSize
            return true
        }

        override fun remove(key: Any?, value: Any?): Boolean {
            throw UnsupportedOperationException()
        }

        override fun putAll(key: String, values: Iterable<ResourceItem>): Boolean {
            if (values is Collection<*>) {
                if (values.isEmpty()) {
                    return false
                }

                val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
                val oldSize = list.size
                val added = list.addAll(values as Collection<ResourceItem>)
                size += list.size - oldSize
                return added
            }

            var added = false
            var list: MutableList<ResourceItem>? = null
            var oldSize = 0
            for (value in values) {
                if (null == list) {
                    list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
                    oldSize = list.size
                }
                added = list.add(value)
            }

            if (list != null) {
                size += list.size - oldSize
            }

            return added
        }

        override fun putAll(multimap: Multimap<out String, out ResourceItem>): Boolean {
            multimap.asMap().entries.forEach { (key, items) ->
                if (items.isNotEmpty()) {
                    val list = map.computeIfAbsent(key) { _ -> PerConfigResourceList() }
                    val oldSize = list.size
                    list += items
                    size += list.size - oldSize
                }
            }
            return !multimap.isEmpty
        }

        override fun replaceValues(key: String?, values: Iterable<ResourceItem>): List<ResourceItem> {
            throw UnsupportedOperationException()
        }

        override fun asMap(): Map<String, Collection<ResourceItem>> = map

        private inner class PerConfigResourceList : ArrayList<ResourceItem>() {

            private val resourceItems = arrayListOf<MutableList<ResourceItem>>()

            override val size: Int
                get() = resourceItems.size

            override fun get(index: Int): ResourceItem = resourceItems[index].first()

            override fun add(element: ResourceItem): Boolean {
                add(element, 0)
                return true
            }

            override fun addAll(elements: Collection<ResourceItem>): Boolean {
                if (elements.isEmpty()) {
                    return false
                }

                if (elements.size == 1) {
                    return add(elements.first())
                }

                val sortedItems = elements.sortedWith(comparator)
                var start = 0
                for (item in sortedItems) {
                    start = add(item, start)
                }
                return true
            }

            private fun add(item: ResourceItem, start: Int): Int {
                var index = findConfigIndex(item, start, resourceItems.size)
                if (index < 0) {
                    index = index.inv()
                    resourceItems.add(index, mutableListOf(item))
                } else {
                    val nested = resourceItems[index]
                    var i = nested.size
                    while (--i >= 0) {
                        if (comparator.priorityComparator.compare(item, nested[i]) > 0) {
                            break
                        }
                    }
                    nested.add(i + 1, item)
                }
                return index
            }

            private fun findConfigIndex(item: ResourceItem, start: Int, end: Int): Int {
                val config = item.configuration
                var low = start
                var high = end
                while (low < high) {
                    val mid = low + high ushr 1
                    val result = resourceItems[mid].first().configuration.compareTo(config)
                    if (result < 0) {
                        low = mid + 1
                    } else if (result > 0) {
                        high = mid
                    } else {
                        return mid
                    }
                }
                return low.inv() // not found
            }
        }

        private inner class Values(override val size: Int) : AbstractCollection<ResourceItem>() {
            override fun iterator(): Iterator<ResourceItem> = map.values.asSequence().flatten().iterator()
        }
    }

}