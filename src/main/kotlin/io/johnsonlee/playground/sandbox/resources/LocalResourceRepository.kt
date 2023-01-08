package io.johnsonlee.playground.sandbox.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.AbstractResourceRepository
import com.android.ide.common.resources.ResourceItem
import com.android.resources.ResourceType
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ListMultimap

/**
 * Repository for Android application resources, e.g. those that show up in `R, not `android.R`
 * (which are referred to as framework resources.). Note that this includes resources from Gradle libraries
 * too, even though you may not think of these as "local" (they do however (a) end up in the application
 * namespace, and (b) get extracted by Gradle into the project's build folder where they are merged with
 * the other resources.)
 *
 * For a given Android module, you can obtain either the resources for the module itself, or for a module and all
 * its libraries. Most clients should use the module with all its dependencies included; when a user is
 * using code completion for example, they expect to be offered not just the drawables in this module, but
 * all the drawables available in this module which includes the libraries.
 *
 * The module repository is implemented using several layers. Consider a Gradle project where the main module has
 * two flavors, and depends on a library module. In this case, the [LocalResourceRepository] for
 * the module with dependencies will contain these components:
 * - A [AppResourceRepository] which contains a [com.android.resources.aar.AarResourceRepository] wrapping each AAR
 *   library dependency, and merges this with the project resource repository
 * - A [ProjectResourceRepository] representing the collection of module repositories
 * - For each module (e.g. the main module and library module), a [ModuleResourceRepository]
 * - For each resource directory in each module, a [ResourceFolderRepository]
 *
 * These different repositories are merged together by the [MultiResourceRepository] class,
 * which represents a repository that just combines the resources from each of its children.
 * All of [AppResourceRepository], [ModuleResourceRepository] and
 * [ProjectResourceRepository] are instances of a [MultiResourceRepository].
 *
 * The [ResourceFolderRepository] is the lowest level of repository. It is associated with just
 * a single resource folder. Therefore, it does not have to worry about trying to mask resources between
 * different flavors; that task is done by the [ModuleResourceRepository] which combines
 * [ResourceFolderRepository] instances. Instead, the [ResourceFolderRepository] just
 * needs to compute the resource items for the resource folders, including qualifier variations.
 *
 * The resource repository automatically stays up to date. You can call `getModificationCount()`
 * to see whether anything has changed since your last data fetch. This is for example how the resource
 * string folding in the source editors work; they fetch the current values of the resource strings, and
 * store those along with the current project resource modification count into the folding data structures.
 * When the editor wants to see if the folding sections are up to date, those are compared with the current
 * `getModificationCount()` version, and only if they differ is the folding structure updated.
 *
 * Only the [ResourceFolderRepository] needs to listen for user edits and file changes. It
 * uses [AndroidFileChangeListener], a single listener which is shared by all repositories in the
 * same project, to get notified when something in one of its resource files changes, and it uses the
 * PSI change event to selectively update the repository data structures, if possible.
 *
 * The [ResourceFolderRepository] can also have a pointer to its parent. This is possible
 * since a resource folder can only be in a single module. The parent reference is used to quickly
 * invalidate the cache of the parent [MultiResourceRepository]. For example, let's say the
 * project has two flavors. When the PSI change event is used to update the name of a string resource,
 * the repository will also notify the parent that its [ResourceType.ID] map is out of date.
 * The [MultiResourceRepository] will use this to null out its map cache of strings, and
 * on the next read, it will merge in the string maps from all its [ResourceFolderRepository]
 * children.
 *
 * One common type of "update" is changing the current variant in the IDE. With the above scheme,
 * this just means reordering the [ResourceFolderRepository] instances in the
 * [ModuleResourceRepository]; it does not have to rescan the resources as it did in the
 * previous implementation.
 *
 * The [ProjectResourceRepository] is similar, but it combines [ModuleResourceRepository]
 * instances rather than [ResourceFolderRepository] instances. Note also that the way these
 * resource repositories work is slightly different from the way the resource items are used by
 * the builder: The builder will bail if it encounters duplicate declarations unless they are in alternative
 * folders of the same flavor. For the resource repository we never want to bail on merging; the repository
 * is kept up to date and live as the user is editing, so it is normal for the repository to sometimes
 * reflect invalid user edits (in the same way a Java editor in an IDE sometimes is showing uncompilable
 * source code) and it needs to be able to handle this case and offer a state that is as close to possible
 * as the intended meaning. Error handling is done by another part of the IDE.
 *
 * Finally, note that the resource repository is showing the current state of the resources for the
 * currently selected variant. Note however that the above approach also lets us query resources for
 * example for <b>all</b> flavors, not just the currently selected flavor. We can offer APIs to iterate
 * through all available [ResourceFolderRepository] instances, not just the set of instances for
 * the current module's current flavor. This will allow us to for example preview the string translations
 * for a given resource name not just for the current flavor but for all other flavors as well.
 *
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:render-resources/src/com/android/tools/res/LocalResourceRepository.java
 */
abstract class LocalResourceRepository protected constructor(
    @JvmField
    val displayName: String
) : AbstractResourceRepository() {

    fun getDisplayName(): String = displayName

    protected abstract fun getMap(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem>?

    override fun getResourcesInternal(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem> {
        val map = getMap(namespace, resourceType)
        return map ?: ImmutableListMultimap.of()
    }

    override fun getPublicResources(
        namespace: ResourceNamespace?,
        type: ResourceType?
    ): MutableCollection<ResourceItem> {
        TODO("Not yet implemented")
    }

    open fun getMapPackageAccessible(
        namespace: ResourceNamespace,
        resourceType: ResourceType
    ): ListMultimap<String, ResourceItem>? = getMap(namespace, resourceType)

}