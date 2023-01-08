package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.base.BasicResourceItem
import com.android.resources.base.RepositoryConfiguration
import com.android.resources.base.ResourceSourceFile
import com.android.utils.Base128OutputStream
import it.unimi.dsi.fastutil.objects.Object2IntMap
import java.io.File

class ResourceFile(
    val file: File?,
    override val configuration: RepositoryConfiguration
) : ResourceSourceFile, Iterable<BasicResourceItem> {

    private val items = mutableListOf<BasicResourceItem>()

    val folderConfiguration: FolderConfiguration
        get() = configuration.folderConfiguration

    override val repository: ResourceFolderRepository
        get() = configuration.repository as ResourceFolderRepository

    override val relativePath: String?
        get() = file?.let {
            repository.resourceDir.toRelativeString(it)
        }

    override fun iterator(): Iterator<BasicResourceItem> = items.iterator()

    override fun serialize(stream: Base128OutputStream, configIndexes: Object2IntMap<String>) {
        TODO("Not yet implemented")
    }

    fun addItem(item: BasicResourceItem) {
        items += item
    }

    fun isValid(): Boolean = file != null
}