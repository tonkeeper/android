package com.tonapps.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.getByteArray
import com.tonapps.log.L
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.read
import kotlin.concurrent.write

sealed class KeyHelperException(message: String? = null, e: Throwable? = null) : Throwable(message, e) {
    class GetMnemonic: KeyHelperException("Can't get mnemonic", null)
    class AddMnemonic: KeyHelperException("Can't add mnemonic", null)
    class GetPkFromMnemonic : KeyHelperException("Can't get private key from mnemonic", null)

    class Delete(message: String? = null, e: Throwable? = null) : KeyHelperException(message, e)
    class Save(message: String? = null, e: Throwable? = null) : KeyHelperException(message, e)
    class Create(message: String? = null, e: Throwable? = null) : KeyHelperException(message, e)
}

class SecurityStorageBox(
    private val prefs: SharedPreferences
) {
    companion object {

        private val locker = ReentrantReadWriteLock() // TODO make for different names

        fun create(context: Context, keyAlias: String, name: String) : SecurityStorageBox {
            locker.write {

                try {
                    KeyHelper.createIfNotExists(keyAlias)

                    val prefs = EncryptedSharedPreferences.create(
                        name,
                        keyAlias,
                        context,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )

                    return SecurityStorageBox(prefs)
                } catch (e: Throwable) {
                    FirebaseCrashlytics.getInstance()
                        .recordException(KeyHelperException.Create("Can't create new KeyStore", e))

                    throw e
                }
            }
        }
    }


    fun put(key: String, value: String?) {
        locker.write {
            prefs.editor {
                putString(key, value)
            }
        }
    }

    fun transaction(action: SharedPreferences.Editor.() -> Unit): Boolean {
        locker.write {
            return prefs.editor {
                action()
            }
        }
    }

    fun contains(key: String): Boolean {
        return locker.read {
            prefs.contains(key)
        }
    }

    fun all(): Map<String, *> {
        return locker.read {
            prefs.all
        }
    }

    fun get(key: String): String? {
        return locker.read {
            prefs.getString(key, null)
        }
    }

    @Deprecated("Don't use it anymore")
    fun putIfNotExist(key: String, value: String): Boolean {
        return locker.write {
            if (!prefs.contains(key)) {
                prefs.editor { putString(key, value) }
            } else {
                true
            }
        }
    }

    fun getByteArray(key: String): ByteArray? {
        return locker.read {
            prefs.getByteArray(key)
        }
    }

    fun clear(): Boolean {
        return locker.write {
            prefs.editor {
                clear()
            }
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private inline fun SharedPreferences.editor(
        action: SharedPreferences.Editor.() -> Unit
    ): Boolean {
        val editor = edit()
        action(editor)
        return editor.commit()
    }
}

object Security {

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        val keySpec = SecretKeySpec(key, "HmacSHA512")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    fun sha256(input: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input)
    }

    fun sha256(input: String): ByteArray {
        return sha256(input.toByteArray())
    }

    fun pref(context: Context, keyAlias: String, name: String): SecurityStorageBox {
        return SecurityStorageBox.create(context, keyAlias, name)
    }

    fun generatePrivateKey(keySize: Int): SecretKey {
        return try {
            val generator = KeyGenerator.getInstance("AES")
            val random = secureRandom()
            generator.init(keySize * 8, random)
            generator.generateKey()
        } catch (e: Throwable) {
            L.e(e, "Error during private key generation")

            throw KeyHelperException.Create("Failed to generate AES SK").also {
                FirebaseCrashlytics.getInstance()
                    .recordException(it)
            }
        }
    }

    fun calcVerification(input: ByteArray, size: Int): ByteArray {
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(input)
        val digest = messageDigest.digest()
        val verification = ByteArray(size)
        digest.copyInto(verification, 0, 0, size)
        digest.clear()
        return verification
    }

    fun argon2Hash(password: CharArray, salt: ByteArray): ByteArray? {
        return Sodium.argon2IdHash(password, salt, 32)
    }

    fun randomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        secureRandom().nextBytes(bytes)
        return bytes
    }

    fun secureRandom(): SecureRandom {
        return SecureRandom.getInstanceStrong()
    }

    fun isAdbEnabled(context: Context): Boolean {
        return isAdbEnabled1(context) || isAdbEnabled2(context)
    }

    private fun isAdbEnabled1(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) != 0
    }

    private fun isAdbEnabled2(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            "adb_port", 0
        ) != 0
    }

    fun isDevelopmentEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
            ) > 0
        } catch (e: Throwable) {
            false
        }
    }

    fun isSupportStrongBox(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        } else {
            false
        }
    }

    fun isDeviceRooted(): Boolean {
        return false
    }

    fun isDebuggable(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
