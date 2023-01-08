package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import java.io.File

/**
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/ProjectResourceRepository.java
 */
class ProjectResourceRepository private constructor(
    displayName: String,
    localResources: List<LocalResourceRepository>,
) : MultiResourceRepository("${displayName} with modules") {

    init {
        setChildren(localResources, emptyList())
    }

    companion object {

        fun create(
            resourceDirectories: List<File>,
            moduleResourceDirectories: List<File>,
        ) = ProjectResourceRepository(
            displayName = "main",
            localResources = buildList {
                this += getModuleResources(resourceDirectories)
                for (moduleResourceDirectory in moduleResourceDirectories) {
                    this += getModuleResources(listOf(moduleResourceDirectory))
                }
            },
        )

        private fun getModuleResources(
            resourceDirectories: List<File>
        ): LocalResourceRepository = ModuleResourceRepository.forMainResources(
            namespace = getNamespace(namespacing = ResourceNamespacing.DISABLED, packageName = "TODO"),
            resourceDirectories = resourceDirectories
        )

        private fun getNamespace(namespacing: ResourceNamespacing, packageName: String?): ResourceNamespace {
            if (namespacing == ResourceNamespacing.DISABLED || packageName == null) {
                return ResourceNamespace.RES_AUTO
            }
            return ResourceNamespace.fromPackageName(packageName)
        }
    }

}