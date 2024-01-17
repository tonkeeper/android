package com.tonapps.singer.core.flow

import kotlinx.coroutines.flow.MutableStateFlow

fun <D> mutableResourceFlow(): MutableStateFlow<Resource<D>> {
    return MutableStateFlow(Resource.Loading())
}
