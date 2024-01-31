package security.vault

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import security.clear
import security.tryCallGC
import javax.crypto.SecretKey

fun Vault.getString(secret: SecretKey, id: Long): String {
    val data = get(secret, id)
    val string = String(data)
    data.clear()
    return string
}

fun Vault.putString(secret: SecretKey, id: Long, string: String) {
    val data = string.toByteArray()
    put(secret, id, data)
}

fun SecretKey.destroyWithGC() {
    try {
        destroy()
    } catch (ignored: Throwable) {}
    tryCallGC()
}

inline fun <R> Flow<SecretKey>.safeArea(
    crossinline transform: suspend (value: SecretKey) -> R
): Flow<R> = transform { value ->
    val transformed = try {
        transform(value)
    } catch (e: Throwable) {
        value.destroyWithGC()
        throw e
    }
    value.destroyWithGC()
    return@transform emit(transformed)
}