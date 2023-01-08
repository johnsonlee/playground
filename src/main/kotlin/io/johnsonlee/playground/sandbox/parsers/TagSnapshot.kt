package io.johnsonlee.playground.sandbox.parsers

data class TagSnapshot(
    val name: String,
    val namespace: String,
    val prefix: String?,
    val attributes: List<AttributeSnapshot>,
    val children: List<TagSnapshot>,
    val hasDeclaredAaptAttrs: Boolean = false
) {

    internal var next: TagSnapshot? = null

}