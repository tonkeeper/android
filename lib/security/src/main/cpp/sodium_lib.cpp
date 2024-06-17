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
    jbyte *native_src_plain = env->GetByteArrayElements(src_plain, NULL);
    jbyte *native_nonce = env->GetByteArrayElements(nonce, NULL);
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, NULL);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, NULL);

    jbyte *native_dst_cipher = env->GetByteArrayElements(dst_cipher, NULL);

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
    jbyte *native_src_cipher = env->GetByteArrayElements(src_cipher, NULL);
    jbyte *native_nonce = env->GetByteArrayElements(nonce, NULL);
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, NULL);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, NULL);

    jbyte *native_dst_plain = env->GetByteArrayElements(dst_plain, NULL);

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
    jbyte *native_remote_public_key = env->GetByteArrayElements(remote_public_key, NULL);
    jbyte *native_local_private_key = env->GetByteArrayElements(local_private_key, NULL);

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
    jchar *native_passwd = env->GetCharArrayElements(passwd, NULL);
    jsize passwdlen = env->GetArrayLength(passwd) * 2;

    if (sodium_mlock(native_passwd, passwdlen) != 0) {
        env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
        return NULL;
    }

    jbyte *native_salt = env->GetByteArrayElements(salt, NULL);

    char *out = (char *) malloc(outlen);

    if (out == NULL || sodium_mlock(out, outlen) != 0) {
        sodium_memzero(native_passwd, passwdlen);
        sodium_munlock(native_passwd, passwdlen);
        env->ReleaseCharArrayElements(passwd, native_passwd, JNI_ABORT);
        if (out != NULL) free(out);
        return NULL;
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
        return NULL;
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
    jbyte *native_password = env->GetByteArrayElements(password, NULL);
    jsize passwordlen = env->GetArrayLength(password);

    if (sodium_mlock(native_password, passwordlen) != 0) {
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        return NULL;
    }

    jbyte *native_salt = env->GetByteArrayElements(salt, NULL);
    jsize saltlen = env->GetArrayLength(salt);

    if (sodium_mlock(native_salt, saltlen) != 0) {
        sodium_munlock(native_password, passwordlen);
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        return NULL;
    }

    char *out = (char *) malloc(dkLen);

    if (out == NULL || sodium_mlock(out, dkLen) != 0) {
        sodium_memzero(native_password, passwordlen);
        sodium_munlock(native_password, passwordlen);
        env->ReleaseByteArrayElements(password, native_password, JNI_ABORT);
        sodium_memzero(native_salt, saltlen);
        sodium_munlock(native_salt, saltlen);
        env->ReleaseByteArrayElements(salt, native_salt, JNI_ABORT);
        if (out != NULL) free(out);
        return NULL;
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
        return NULL;
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
    jbyte *native_box = env->GetByteArrayElements(box, NULL);
    jsize boxlen = env->GetArrayLength(box);

    if (sodium_mlock(native_box, boxlen) != 0) {
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
        return NULL;
    }

    jbyte *native_none = env->GetByteArrayElements(none, NULL);
    jsize nonelen = env->GetArrayLength(none);

    if (sodium_mlock(native_none, nonelen) != 0) {
        sodium_munlock(native_box, boxlen);
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
        env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
        return NULL;
    }

    jbyte *native_key = env->GetByteArrayElements(key, NULL);
    jsize keylen = env->GetArrayLength(key);

    if (sodium_mlock(native_key, keylen) != 0) {
        sodium_munlock(native_box, boxlen);
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
        sodium_munlock(native_none, nonelen);
        env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
        env->ReleaseByteArrayElements(key, native_key, JNI_ABORT);
        return NULL;
    }

    char *out = (char *) malloc(boxlen);

    if (out == NULL || sodium_mlock(out, boxlen) != 0) {
        sodium_memzero(native_box, boxlen);
        sodium_munlock(native_box, boxlen);
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
        sodium_memzero(native_none, nonelen);
        sodium_munlock(native_none, nonelen);
        env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
        sodium_memzero(native_key, keylen);
        sodium_munlock(native_key, keylen);
        env->ReleaseByteArrayElements(key, native_key, JNI_ABORT);
        if (out != NULL) free(out);
        return NULL;
    }

    int result = crypto_secretbox_open_easy(
            reinterpret_cast<unsigned char *const>(out),
            reinterpret_cast<const unsigned char *const>(native_box),
            boxlen,
            reinterpret_cast<const unsigned char *const>(native_none),
            reinterpret_cast<const unsigned char *const>(native_key)
    );

    if (result != 0) {
        sodium_memzero(out, boxlen);
        sodium_munlock(out, boxlen);
        free(out);
        sodium_memzero(native_box, boxlen);
        sodium_munlock(native_box, boxlen);
        env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
        sodium_memzero(native_none, nonelen);
        sodium_munlock(native_none, nonelen);
        env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
        sodium_memzero(native_key, keylen);
        sodium_munlock(native_key, keylen);
        env->ReleaseByteArrayElements(key, native_key, JNI_ABORT);
        return NULL;
    }

    jbyteArray jplain = env->NewByteArray(boxlen);
    env->SetByteArrayRegion(jplain, 0, boxlen, (jbyte *) out);

    sodium_memzero(out, boxlen);
    sodium_munlock(out, boxlen);
    free(out);
    sodium_memzero(native_box, boxlen);
    sodium_munlock(native_box, boxlen);
    env->ReleaseByteArrayElements(box, native_box, JNI_ABORT);
    sodium_memzero(native_none, nonelen);
    sodium_munlock(native_none, nonelen);

    env->ReleaseByteArrayElements(none, native_none, JNI_ABORT);
    sodium_memzero(native_key, keylen);
    sodium_munlock(native_key, keylen);
    env->ReleaseByteArrayElements(key, native_key, JNI_ABORT);

    return jplain;
}



