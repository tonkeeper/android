-keepattributes *Annotation*
-keepclassmembers class com.ton_keeper.** {
    @org.jetbrains.annotations.** <fields>;
    @org.jetbrains.annotations.** <methods>;
}

-keep class io.tonapi.** { *; }

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

# Strip all Android logging for security and performance
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}