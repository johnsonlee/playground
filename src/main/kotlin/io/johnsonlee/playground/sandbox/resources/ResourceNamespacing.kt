package io.johnsonlee.playground.sandbox.resources

enum class ResourceNamespacing {
    /**
     * Resources are not namespaced.
     */
    DISABLED,

    /**
     * Resources must be namespaced.
     */
    REQUIRED
}