package io.johnsonlee.playground.sandbox.parsers

import com.android.SdkConstants

class AaptAttrSnapshot(
    override val namespace: String,
    override val prefix: String,
    override val name: String,
    val id: String,
    val bundledTag: TagSnapshot
) : AttributeSnapshot(namespace, prefix, name, "${SdkConstants.AAPT_ATTR_PREFIX}${SdkConstants.AAPT_PREFIX}${id}")