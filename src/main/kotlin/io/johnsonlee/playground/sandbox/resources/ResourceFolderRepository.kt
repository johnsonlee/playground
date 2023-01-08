package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.rendering.api.DensityBasedResourceValue
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceVisitor
import com.android.ide.common.util.PathString
import com.android.resources.Density
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.base.BasicFileResourceItem
import com.android.resources.base.BasicResourceItem
import com.android.resources.base.BasicValueResourceItemBase
import com.android.resources.base.LoadableResourceRepository
import com.android.resources.base.RepositoryConfiguration
import com.android.resources.base.RepositoryLoader
import com.android.resources.base.ResourceSourceFile
import com.android.utils.SdkUtils
import com.google.common.collect.LinkedListMultimap
import com.google.common.collect.ListMultimap
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.EnumMap
import javax.lang.model.SourceVersion
import kotlin.io.path.exists

/**
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/ResourceFolderRepository.java
 */
class ResourceFolderRepository(
    val resourceDir: File,
    private val namespace: ResourceNamespace
) : LocalResourceRepository(resourceDir.name), LoadableResourceRepository {

    private val resourcePathPrefix = "${resourceDir.path}/"

    private val resourcePathBase: PathString = PathString(resourcePathPrefix)

    private val resourceTable = EnumMap<ResourceType, ListMultimap<String, ResourceItem>>(ResourceType::class.java)

    init {
        Loader(this).load()
    }

    override fun getLibraryName(): String? = null

    override fun getOrigin(): Path = Paths.get(resourceDir.path)

    override fun getResourceUrl(relativeResourcePath: String): String = "${resourcePathPrefix}${relativeResourcePath}"

    override fun getSourceFile(
        relativeResourcePath: String,
        forFileResource: Boolean
    ): PathString = resourcePathBase.resolve(relativeResourcePath)

    override fun getPackageName(): String = namespace.packageName

    override fun containsUserDefinedResources(): Boolean = true

    override fun accept(visitor: ResourceVisitor): ResourceVisitor.VisitResult {
        if (visitor.shouldVisitNamespace(namespace)) {
            if (acceptByResources(resourceTable, visitor) == ResourceVisitor.VisitResult.ABORT) {
                return ResourceVisitor.VisitResult.ABORT
            }
        }
        return ResourceVisitor.VisitResult.CONTINUE
    }

    override fun getMap(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem>? = if (namespace != this.namespace) {
        null
    } else {
        resourceTable[resourceType]
    }

    override fun getNamespace(): ResourceNamespace = namespace

    private fun checkResourceFilename(file: PathString): Boolean {
        val fileNameToResourceName = SdkUtils.fileNameToResourceName(file.fileName)
        return SourceVersion.isIdentifier(fileNameToResourceName) && !SourceVersion.isKeyword(fileNameToResourceName)
    }

    private fun commitToRepository(itemsByType: Map<ResourceType, ListMultimap<String, ResourceItem>>) {
        itemsByType.forEach { (type, items) ->
            getOrCreateMap(type).putAll(items)
        }
    }

    private fun getOrCreateMap(type: ResourceType): ListMultimap<String, ResourceItem> {
        return resourceTable.computeIfAbsent(type) { LinkedListMultimap.create() }
    }

    private class Loader(
        private val repository: ResourceFolderRepository
    ) : RepositoryLoader<ResourceFolderRepository>(
        repository.resourceDir.toPath(),
        null,
        repository.namespace
    ) {
        private val resourceDir = repository.resourceDir
        private val resources = EnumMap<ResourceType, ListMultimap<String, ResourceItem>>(ResourceType::class.java)
        private val sources = mutableMapOf<File, ResourceFile>()
        private val fileResources = mutableMapOf<File, BasicFileResourceItem>()

        private var lastFile: File? = null
        private var lastPathString: PathString? = null

        override fun addResourceItem(item: BasicResourceItem, repository: ResourceFolderRepository) {
            when (item) {
                is BasicValueResourceItemBase -> {
                    val sourceFile = item.sourceFile as ResourceFile
                    val file = sourceFile.file
                    if (file != null && !file.isDirectory) {
                        sourceFile.addItem(item)
                        sources[file] = sourceFile
                    }
                }

                is BasicFileResourceItem -> {
                    val file = getFile(item.source)
                    if (file != null && !file.isDirectory) {
                        fileResources[file] = item
                    }
                }

                else -> throw IllegalArgumentException("Unsupported resource item: ${item.javaClass.name}")
            }
        }

        override fun createResourceSourceFile(
            file: PathString,
            configuration: RepositoryConfiguration
        ): ResourceSourceFile = ResourceFile(getFile(file), configuration)

        fun load() {
            if (!resourceDirectoryOrFile.exists()) {
                return
            }

            scanResFolder()
            repository.commitToRepository(resources)
        }

        private fun getFile(pathString: PathString): File? = if (pathString == lastPathString) {
            lastFile
        } else {
            pathString.toFile()
        }

        private fun scanResFolder() {
            try {
                resourceDir.listFiles()?.filter(File::isDirectory)?.sorted()?.forEach root@{ subDir ->
                    val folderName = subDir.name
                    val folderInfo = FolderInfo.create(folderName, myFolderConfigCache) ?: return@root
                    val configuration = getConfiguration(repository, folderInfo.configuration)
                    subDir.listFiles()?.filter {
                        it.name.startsWith(".")
                    }?.sorted()?.forEach sub@{ file ->
                        if (file in (if (folderInfo.folderType == ResourceFolderType.VALUES) sources else fileResources)) {
                            return@sub
                        }

                        val pathString = PathString(file)
                        lastFile = file
                        lastPathString = pathString
                        loadResourceFile(pathString, folderInfo, configuration)
                    }
                }
            } catch (e: Throwable) {
                logger.warn("Failed to load resources from ${resourceDirectoryOrFile}", e)
            }

            super.finishLoading(repository)

            fileResources.entries.forEach { (file, item) ->
                val source = sources.computeIfAbsent(file) {
                    ResourceFile(it, item.repositoryConfiguration)
                }
                source.addItem(item)
            }

            sources.values.toMutableList().sortedWith(SOURCE_COMPARATOR).forEach { source ->
                source.forEach { item ->
                    resources.getOrPut(item.type) {
                        LinkedListMultimap.create()
                    }.put(item.name, item)
                }
            }
        }

        private fun loadResourceFile(
            file: PathString,
            folderInfo: FolderInfo,
            configuration: RepositoryConfiguration
        ) {
            if (folderInfo.resourceType == null) {
                if (isXmlFile(file)) {
                    parseValueResourceFile(file, configuration)
                }
            } else if (repository.checkResourceFilename(file)) {
                if (isXmlFile(file) && folderInfo.isIdGenerating) {
                    parseIdGeneratingResourceFile(file, configuration)
                }
                val item = createFileResourceItem(file, folderInfo.resourceType, configuration)
                addResourceItem(item, item.repository as ResourceFolderRepository)
            }
        }

        fun createFileResourceItem(
            file: PathString,
            resourceType: ResourceType,
            configuration: RepositoryConfiguration
        ): BasicFileResourceItem {
            val resourceName = SdkUtils.fileNameToResourceName(file.fileName)
            val visibility = getVisibility(resourceType, resourceName)
            val density: Density? = configuration.takeIf {
                DensityBasedResourceValue.isDensityBasedResourceType(resourceType)
            }?.folderConfiguration?.densityQualifier?.value
            return createFileResourceItem(file, resourceType, resourceName, configuration, visibility, density)
        }

        companion object {
            private val logger = LoggerFactory.getLogger(Loader::class.java)
            private val SOURCE_COMPARATOR = Comparator.comparing(ResourceFile::folderConfiguration)
        }
    }
}