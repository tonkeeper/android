#include <jni.h>
#include <malloc.h>
#include <string>
#include <sodium.h>

extern "C" JNIEXPORT jint JNICALL Java_com_tonapps_security_Sodium_init(JNIEnv *, jobject) {
    return sodium_init();
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
