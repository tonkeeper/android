-keepattributes *Annotation*
-keepclassmembers class com.ton_keeper.** {
    @org.jetbrains.annotations.** <fields>;
    @org.jetbrains.annotations.** <methods>;
}

-keep class io.tonapi.** { *; }

-keep class io.ton.walletkit.** { *; }

# Tink KeysDownloader - optional Google HTTP client and Joda Time deps not used at runtime
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.**

-keep class io.batteryapi.** { *; }

-keep class com.google.j2objc.annotations.** { *; }

# Keep enum values to ensure correct deserialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep methods annotated with Retrofit annotations
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Moshi generated adapter methods
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepnames class * implements android.os.Parcelable

-keep class java.time.** { *; }

-keep class com.tonapps.tonkeeper.worker.** { *; }

-keep class com.tonapps.tonkeeper.manager.** { *; }

-keep class android.graphics.ColorSpace { *; }
-dontwarn android.graphics.ColorSpace
-dontwarn android.graphics.ColorSpace$**

-keep class org.koin.** { *; }
-keep class com.tonapps.tonkeeper.App { *; }

-keep class androidx.lifecycle.SavedStateHandle { *; }

-keepnames class com.tonapps.tonkeeper.ui.screen.** { *; }

-dontwarn com.fasterxml.jackson.databind.ext.Java7SupportImpl
-keep class com.fasterxml.jackson.databind.ext.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.w3c.dom.**
-dontwarn com.fasterxml.jackson.databind.ext.DOMSerializer

-keep class com.facebook.imagepipeline.** { *; }
-dontwarn com.facebook.imagepipeline.**
-keep class com.facebook.imageutils.** { *; }
-dontwarn com.facebook.imageutils.**

# Cronet - ignore missing classes
-dontwarn org.chromium.**
-keep class org.chromium.** { *; }

# Tink KeysDownloader - optional Google HTTP client and Joda Time deps not used at runtime
-dontwarn com.google.api.client.http.**
-dontwarn com.google.api.client.http.javanet.**
-dontwarn org.joda.time.**

# Strip all Android logging for security and performance
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# WalletKit
-keep class io.ton.walletkit.** { *; }

# WalletKit resolves its bridge-DTO serializers reflectively
# (serializersModule.serializer(klass)) and ships those @Serializable DTOs obfuscated and
# package-flattened, so each DTO's Companion is a separate top-level class, not a nested
# $Companion. That defeats both the stock kotlinx rules (keyed on $Companion) and
# `-keep @Serializable` (which misses the companion's serializer()), and R8 full mode then
# strips the reflectively-only serializer -> "Serializer for class 'x' is not found".
# Keep the reflective serializer entry points structurally, not by package/annotation.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-keepclassmembers @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation class * implements kotlinx.serialization.KSerializer { *; }

# JNA — native code resolves fields (e.g. com.sun.jna.Pointer#peer) and
# Structure subclass field order by name via JNI/reflection. Stripping or
# renaming them yields UnsatisfiedLinkError: "Can't obtain peer field ID".
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }

# UniFFI-generated bindings (Reown WalletKit / yttrium_wcpay) declare JNA
# Structure subclasses whose field order must be preserved.
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }
-keepnames class * extends androidx.fragment.app.Fragment
-keepnames class * extends android.app.Fragment
