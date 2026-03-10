package com.tonapps.wallet.data.rn.expo

internal class NullKeyException :
    Exception("SecureStore keys must not be null")

internal class WriteException(message: String?, key: String, keychain: String, cause: Throwable? = null) :
    Exception("An error occurred when writing to key: '$key' under keychain: '$keychain'. Caused by: ${message ?: "unknown"}", cause)

internal class EncryptException(message: String?, key: String, keychain: String, cause: Throwable? = null) :
    Exception("Could not encrypt the value for key '$key' under keychain '$keychain'. Caused by: ${message ?: "unknown"}", cause)

internal class DecryptException(message: String?, key: String, keychain: String, cause: Throwable? = null) :
    Exception("Could not decrypt the value for key '$key' under keychain '$keychain'. Caused by: ${message ?: "unknown"}", cause)

internal class DeleteException(message: String?, key: String, keychain: String, cause: Throwable? = null) :
    Exception("Could not delete the value for key '$key' under keychain '$keychain'. Caused by: ${message ?: "unknown"}", cause)

internal class AuthenticationException(message: String?, cause: Throwable? = null) :
    Exception("Could not Authenticate the user: ${message ?: "unknown"}", cause)

internal class KeyStoreException(message: String?) :
    Exception("An error occurred when accessing the keystore: ${message ?: "unknown"}")

internal class CodedException(val code: String, message: String) :
    Exception(message)