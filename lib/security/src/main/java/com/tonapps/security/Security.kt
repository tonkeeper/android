package com.tonapps.security

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

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

    @Synchronized
    fun pref(context: Context, keyAlias: String, name: String): SharedPreferences {
        try {
            KeyHelper.createIfNotExists(keyAlias)

            return EncryptedSharedPreferences.create(
                name,
                keyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: UserNotAuthenticatedException) {
            openUserAuthentication(context)
            throw e
        } catch (e: Throwable) {
            throw e
        }
    }

    private fun openUserAuthentication(context: Context) {
        try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent = keyguardManager.createConfirmDeviceCredentialIntent("Tonkeeper", "Auth") ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (ignored: Throwable) { }
    }

    fun generatePrivateKey(keySize: Int): SecretKey {
        return try {
            val generator = KeyGenerator.getInstance("AES")
            val random = secureRandom()
            generator.init(keySize * 8, random)
            generator.generateKey()
        } catch (e: Throwable) {
            SecretKeySpec(randomBytes(keySize), "AES")
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SecureRandom.getInstanceStrong()
        } else {
            SecureRandom()
        }
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