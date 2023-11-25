package core.keyvalue

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import core.extensions.toBase64
import core.extensions.toByteArrayFromBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EncryptedKeyValue(
    context: Context,
    name: String
): BaseKeyValue() {

    private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    override val preferences = EncryptedSharedPreferences.create(
        name,
        mainKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

}