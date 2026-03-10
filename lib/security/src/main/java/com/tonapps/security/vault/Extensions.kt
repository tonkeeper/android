package com.tonapps.security.vault

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import com.tonapps.security.clear
import com.tonapps.security.safeDestroy
import com.tonapps.security.tryCallGC
import javax.crypto.SecretKey

suspend fun Vault.getString(secret: SecretKey, id: Long): String {
    val data = get(secret, id)
    val string = String(data)
    data.clear()
    return string
}

suspend fun Vault.putString(secret: SecretKey, id: Long, string: String) {
    val data = string.toByteArray()
    put(secret, id, data)
}

inline fun <R> Flow<SecretKey>.safeArea(
    crossinline transform: suspend (value: SecretKey) -> R
): Flow<R> = transform { value ->
    val transformed = try {
        transform(value)
    } catch (e: Throwable) {
        value.safeDestroy()
        throw e
    }
    value.safeDestroy()
    tryCallGC()
    return@transform emit(transformed)
}