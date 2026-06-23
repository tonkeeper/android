package io.extensions

import io.tonapi.models.NftItem

val NftItem.renderType: String
    get() = metadata["render_type"]?.asString() ?: ""