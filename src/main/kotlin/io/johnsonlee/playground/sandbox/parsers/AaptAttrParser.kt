package io.johnsonlee.playground.sandbox.parsers

/**
 * Interface for parsers that support declaration of inlined {@code aapt:attr} attributes
 *
 * @link https://cs.android.com/android-studio/platform/tools/adt/idea/+/mirror-goog-studio-main:rendering/src/com/android/tools/rendering/parsers/AaptAttrParser.java
 */
interface AaptAttrParser {

    /**
     * Returns a {@link ImmutableMap} that contains all the {@code aapt:attr} elements declared in this or any children parsers. This list
     * can be used to resolve {@code @aapt/_aapt} references into this parser.
     */
    fun getAaptDeclaredAttrs(): Map<String, TagSnapshot>

}