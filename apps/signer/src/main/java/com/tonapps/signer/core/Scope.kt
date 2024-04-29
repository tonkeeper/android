package com.tonapps.signer.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object Scope {
    val repositories = CoroutineScope(Dispatchers.IO + SupervisorJob())
}