#include <jni.h>
#include <malloc.h>
#include <string>
#include <sodium.h>

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_init(JNIEnv *, jobject) {
    return sodium_init();
}

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_cryptoBoxMacBytes(JNIEnv *, jobject) {
    return crypto_box_macbytes();
}

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_cryptoBoxNonceBytes(JNIEnv *, jobject) {
    return crypto_box_noncebytes();
}

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_cryptoBoxEasy (
        JNIEnv *env,
        jobject,
        jbyteArray dst_cipher,
        jbyteArray src_plain,
        jint plain_len,
        jbyteArray nonce,
        jbyteArray remote_public_key,
        jbyteArray local_private_key
) {
    jbyte *native_src_plain = env->GetByteArrayElements(src_plain, nullptr);
    jbyte *native_nonce = env->GetByteArrayElements(nonce, nullptr);
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, nullptr);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, nullptr);

    jbyte *native_dst_cipher = env->GetByteArrayElements(dst_cipher, nullptr);

    int result = crypto_box_easy(
            reinterpret_cast<unsigned char *const>(native_dst_cipher),
            reinterpret_cast<const unsigned char *const>(native_src_plain),
            plain_len,
            reinterpret_cast<const unsigned char *const>(native_nonce),
            reinterpret_cast<const unsigned char *const>(native_remote_public_key),
            reinterpret_cast<const unsigned char *const>(native_local_private_key)
    );

    env->ReleaseByteArrayElements(dst_cipher, native_dst_cipher, JNI_COMMIT);
    env->ReleaseByteArrayElements(src_plain, native_src_plain, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, native_nonce, JNI_ABORT);
    env->ReleaseByteArrayElements(remote_public_key, native_remote_public_key, JNI_ABORT);
    env->ReleaseByteArrayElements(local_private_key, native_local_private_key, JNI_ABORT);

    return result;
}

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_cryptoBoxOpenEasy (
        JNIEnv *env,
        jobject,
        jbyteArray dst_plain,
        jbyteArray src_cipher,
        jint cipher_len,
        jbyteArray nonce,
        jbyteArray remote_public_key,
        jbyteArray local_private_key
) {
    jbyte *native_src_cipher = env->GetByteArrayElements(src_cipher, nullptr);
    jbyte *native_nonce = env->GetByteArrayElements(nonce, nullptr);
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, nullptr);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, nullptr);

    jbyte *native_dst_plain = env->GetByteArrayElements(dst_plain, nullptr);

    int result = crypto_box_open_easy(
            reinterpret_cast<unsigned char *const>(native_dst_plain),
            reinterpret_cast<const unsigned char *const>(native_src_cipher),
            cipher_len,
            reinterpret_cast<const unsigned char *const>(native_nonce),
            reinterpret_cast<const unsigned char *const>(native_remote_public_key),
            reinterpret_cast<const unsigned char *const>(native_local_private_key)
    );

    env->ReleaseByteArrayElements(dst_plain, native_dst_plain, JNI_COMMIT);
    env->ReleaseByteArrayElements(src_cipher, native_src_cipher, JNI_ABORT);
    env->ReleaseByteArrayElements(nonce, native_nonce, JNI_ABORT);
    env->ReleaseByteArrayElements(remote_public_key, native_remote_public_key, JNI_ABORT);
    env->ReleaseByteArrayElements(local_private_key, native_local_private_key, JNI_ABORT);

    return result;
}

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_cryptoBoxKeyPair (
        JNIEnv *env,
        jobject,
        jbyteArray remote_public_key,
        jbyteArray local_private_key
) {
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, nullptr);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, nullptr);

    int result = crypto_box_keypair(
            reinterpret_cast<unsigned char *const>(native_remote_public_key),
            reinterpret_cast<unsigned char *const>(native_local_private_key)
    );

    env->ReleaseByteArrayElements(remote_public_key, native_remote_public_key, JNI_COMMIT);
    env->ReleaseByteArrayElements(local_private_key, native_local_private_key, JNI_COMMIT);

    return result;

}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_tonapps_security_Sodium_argon2IdHash (
        JNIEnv *env,
        jobject,
        jcharArray passwd,
        jbyteArray salt,
        jint outlen
) {
    jchar *native_passwd = env->GetCharArrayElements(passwd, nullptr);
    jsize passwdlen = env->GetArrayLength(passwd) * 2;

    if (sodium_mlock(native_passwd, passwdlen) != 0) {
        env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
        return nullptr;
    }

    jbyte *native_salt = env->GetByteArrayElements(salt, nullptr);

    char *out = (char *) malloc(outlen);

    if (out == nullptr || sodium_mlock(out, outlen) != 0) {
        sodium_memzero(native_passwd, passwdlen);
        sodium_munlock(native_passwd, passwdlen);
        env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
        if (out != nullptr) free(out);
        return nullptr;
    }

    int result = crypto_pwhash_argon2id(
            reinterpret_cast<unsigned char *const>(out), outlen,
            reinterpret_cast<const char *const>(native_passwd), passwdlen,
            reinterpret_cast<const unsigned char *const>(native_salt),
            3U,
            crypto_pwhash_argon2id_MEMLIMIT_INTERACTIVE,
            crypto_pwhash_argon2id_ALG_ARGON2ID13
            );

    if (result != 0) {
        sodium_memzero(out, outlen);
        sodium_munlock(out, outlen);
        free(out);
        sodium_memzero(native_passwd, passwdlen);
        sodium_munlock(native_passwd, passwdlen);

        env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        return nullptr;
    }

    jbyteArray jhash = env->NewByteArray(outlen);
    env->SetByteArrayRegion(jhash, 0, outlen, (jbyte *) out);

    sodium_memzero(out, outlen);
    sodium_munlock(out, outlen);
    free(out);
    sodium_memzero(native_passwd, passwdlen);
    sodium_munlock(native_passwd, passwdlen);

    env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
    env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);

    return jhash;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_tonapps_security_Sodium_scryptHash(
        JNIEnv *env,
        jobject,
        jbyteArray password,
        jbyteArray salt,
        jint n,
        jint r,
        jint p,
        jint dkLen
) {
    jbyte *native_password = env->GetByteArrayElements(password, nullptr);
    jsize passwordlen = env->GetArrayLength(password);

    if (sodium_mlock(native_password, passwordlen) != 0) {
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        return nullptr;
    }

    jbyte *native_salt = env->GetByteArrayElements(salt, nullptr);
    jsize saltlen = env->GetArrayLength(salt);

    if (sodium_mlock(native_salt, saltlen) != 0) {
        sodium_munlock(native_password, passwordlen);
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        return nullptr;
    }

    char *out = (char *) malloc(dkLen);

    if (out == nullptr || sodium_mlock(out, dkLen) != 0) {
        sodium_memzero(native_password, passwordlen);
        sodium_munlock(native_password, passwordlen);
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        sodium_memzero(native_salt, saltlen);
        sodium_munlock(native_salt, saltlen);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        if (out != nullptr) free(out);
        return nullptr;
    }

    int result = crypto_pwhash_scryptsalsa208sha256_ll(
            reinterpret_cast<const uint8_t *const>(native_password), passwordlen,
            reinterpret_cast<const uint8_t *const>(native_salt), saltlen,
            n, r, p,
            reinterpret_cast<uint8_t *const>(out), dkLen
    );

    if (result != 0) {
        sodium_memzero(out, dkLen);
        sodium_munlock(out, dkLen);
        free(out);
        sodium_memzero(native_password, passwordlen);
        sodium_munlock(native_password, passwordlen);
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        sodium_memzero(native_salt, saltlen);
        sodium_munlock(native_salt, saltlen);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        return nullptr;
    }

    jbyteArray jhash = env->NewByteArray(dkLen);
    env->SetByteArrayRegion(jhash, 0, dkLen, (jbyte *) out);

    sodium_memzero(out, dkLen);
    sodium_munlock(out, dkLen);
    free(out);
    sodium_memzero(native_password, passwordlen);
    sodium_munlock(native_password, passwordlen);
    env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
    sodium_memzero(native_salt, saltlen);
    sodium_munlock(native_salt, saltlen);
    env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);

    return jhash;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_tonapps_security_Sodium_cryptoSecretboxOpen(
        JNIEnv *env,
        jobject,
        jbyteArray box,
        jbyteArray none,
        jbyteArray key
) {
    jbyte *native_box = nullptr;
    jbyte *native_none = nullptr;
    jbyte *native_key = nullptr;
    char *out = nullptr;
    jbyteArray jplain = nullptr;
    jsize boxlen = 0;
    jsize nonelen = 0;
    jsize keylen = 0;
    int result = -1;

    if (!box || !none || !key) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Null input parameters");
        return nullptr;
    }

    boxlen = env->GetArrayLength(box);
    nonelen = env->GetArrayLength(none);
    keylen = env->GetArrayLength(key);

    if (nonelen != crypto_secretbox_NONCEBYTES || keylen != crypto_secretbox_KEYBYTES || boxlen < crypto_secretbox_MACBYTES) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid input lengths");
        return nullptr;
    }

    native_box = env->GetByteArrayElements(box, nullptr);
    if (!native_box) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Failed to get box array");
        goto cleanup;
    }

    native_none = env->GetByteArrayElements(none, nullptr);
    if (!native_none) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Failed to get nonce array");
        goto cleanup;
    }

    native_key = env->GetByteArrayElements(key, nullptr);
    if (!native_key) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Failed to get key array");
        goto cleanup;
    }

    if (sodium_mlock(native_box, boxlen) != 0 ||
        sodium_mlock(native_none, nonelen) != 0 ||
        sodium_mlock(native_key, keylen) != 0) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), "Failed to lock memory");
        goto cleanup;
    }

    out = (char *)malloc(boxlen);
    if (!out || sodium_mlock(out, boxlen) != 0) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Failed to allocate output buffer");
        goto cleanup;
    }

    result = crypto_secretbox_open_easy(
            reinterpret_cast<unsigned char *>(out),
            reinterpret_cast<const unsigned char *>(native_box),
            boxlen,
            reinterpret_cast<const unsigned char *>(native_none),
            reinterpret_cast<const unsigned char *>(native_key)
    );

    if (result != 0) {
        env->ThrowNew(env->FindClass("java/lang/SecurityException"), "Decryption failed");
        goto cleanup;
    }

    jplain = env->NewByteArray(boxlen);
    if (!jplain) {
        env->ThrowNew(env->FindClass("java/lang/OutOfMemoryError"), "Failed to create output array");
        goto cleanup;
    }

    env->SetByteArrayRegion(jplain, 0, boxlen, (jbyte *)out);

    cleanup:
    if (out) {
        sodium_memzero(out, boxlen);
        sodium_munlock(out, boxlen);
        free(out);
    }
    if (native_box) {
        sodium_memzero(native_box, boxlen);
        sodium_munlock(native_box, boxlen);
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
    }
    if (native_none) {
        sodium_memzero(native_none, nonelen);
        sodium_munlock(native_none, nonelen);
        env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
    }
    if (native_key) {
        sodium_memzero(native_key, keylen);
        sodium_munlock(native_key, keylen);
        env->ReleaseByteArrayElements(key, native_key, JNI_ABORT);
    }

    return jplain;
}

extern "C" JNIEXPORT jbyteArray JNICALL Java_com_tonapps_security_Sodium_cryptoSecretbox(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray message,
        jbyteArray nonce,
        jbyteArray key
) {
    if (!message || !nonce || !key) {
        return nullptr;
    }

    const jsize messagelen = env->GetArrayLength(message);
    const jsize noncelen = env->GetArrayLength(nonce);
    const jsize keylen = env->GetArrayLength(key);

    if (noncelen != crypto_secretbox_NONCEBYTES || keylen != crypto_secretbox_KEYBYTES) {
        return nullptr;
    }

    class ByteArrayGuard {
    public:
        ByteArrayGuard(JNIEnv* env, jbyteArray array)
                : env_(env), array_(array), data_(nullptr) {
            if (array) {
                data_ = env->GetByteArrayElements(array, nullptr);
            }
        }
        ~ByteArrayGuard() {
            if (data_) {
                env_->ReleaseByteArrayElements(array_, data_, JNI_ABORT);
            }
        }
        jbyte* get() { return data_; }
    private:
        JNIEnv* env_;
        jbyteArray array_;
        jbyte* data_;
    };

    ByteArrayGuard messageGuard(env, message);
    ByteArrayGuard nonceGuard(env, nonce);
    ByteArrayGuard keyGuard(env, key);

    if (!messageGuard.get() || !nonceGuard.get() || !keyGuard.get()) {
        return nullptr;
    }

    const jsize cipherlen = crypto_secretbox_MACBYTES + messagelen;
    auto* cipher = static_cast<unsigned char*>(sodium_malloc(cipherlen));

    if (!cipher) {
        return nullptr;
    }

    struct CipherGuard {
        explicit CipherGuard(unsigned char* ptr) : ptr_(ptr) {}
        ~CipherGuard() {
            if (ptr_) {
                sodium_free(ptr_);
            }
        }
        unsigned char* get() { return ptr_; }
    private:
        unsigned char* ptr_;
    } cipherGuard(cipher);

    const int result = crypto_secretbox_easy(
            cipher,
            reinterpret_cast<const unsigned char*>(messageGuard.get()),
            messagelen,
            reinterpret_cast<const unsigned char*>(nonceGuard.get()),
            reinterpret_cast<const unsigned char*>(keyGuard.get())
    );

    if (result != 0) {
        return nullptr;
    }

    jbyteArray jcipher = env->NewByteArray(cipherlen);
    if (!jcipher) {
        return nullptr;
    }

    env->SetByteArrayRegion(jcipher, 0, cipherlen, reinterpret_cast<jbyte*>(cipher));

    return jcipher;
}