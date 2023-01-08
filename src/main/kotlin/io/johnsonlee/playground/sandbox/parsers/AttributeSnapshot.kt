package io.johnsonlee.playground.sandbox.parsers

open class AttributeSnapshot(
    open val namespace: String,
    open val prefix: String,
    open val name: String,
    open val value: String
) {

    override fun toString() = "${name}: ${value}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttributeSnapshot

        if (namespace != other.namespace) return false
        if (prefix != other.prefix) return false
        if (name != other.name) return false
        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + prefix.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }

}
