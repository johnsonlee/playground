package io.johnsonlee.playground.sandbox.resources

import com.android.resources.aar.AarSourceResourceRepository
import java.io.File

/**
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:android/src/com/android/tools/idea/res/AppResourceRepository.java
 */
class AppResourceRepository private constructor(
    displayName: String,
    localResources: List<LocalResourceRepository>,
    libraryResources: Collection<AarSourceResourceRepository>
) : MultiResourceRepository("${displayName} with modules and libraries") {

    init {
        setChildren(localResources, libraryResources)
    }

    companion object {
        fun create(
            localResourceDirectories: List<File>,
            moduleResourceDirectories: List<File>,
            libraryRepositories: Collection<AarSourceResourceRepository>
        ): AppResourceRepository = AppResourceRepository(
            displayName = "",
            listOf(ProjectResourceRepository.create(localResourceDirectories, moduleResourceDirectories)),
            libraryRepositories
        )
    }
}
