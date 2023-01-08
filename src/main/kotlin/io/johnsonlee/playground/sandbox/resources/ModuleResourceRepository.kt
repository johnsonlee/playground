package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.SingleNamespaceResourceRepository
import java.io.File

/**
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/ModuleResourceRepository.java
 */
class ModuleResourceRepository private constructor(
    displayName: String,
    private val namespace: ResourceNamespace,
    delegate: List<LocalResourceRepository>
): MultiResourceRepository(displayName), SingleNamespaceResourceRepository {

    init {
        setChildren(delegate, emptyList())
    }

    override fun getNamespace(): ResourceNamespace = namespace

    override fun getPackageName(): String = namespace.packageName

    override fun getNamespaces(): Set<ResourceNamespace> = super<MultiResourceRepository>.getNamespaces()

    override fun getLeafResourceRepositories(): Collection<SingleNamespaceResourceRepository> {
        return super<MultiResourceRepository>.getLeafResourceRepositories()
    }

    companion object {
        fun forMainResources(
            namespace: ResourceNamespace,
            resourceDirectories: List<File>
        ): ModuleResourceRepository = ModuleResourceRepository(
            displayName = "main",
            namespace = namespace,
            delegate = buildList {
                resourceDirectories.asReversed().forEach {
                    add(ResourceFolderRepository(it, namespace))
                }
            }
        )
    }

}