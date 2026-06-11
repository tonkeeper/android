-optimizationpasses 20
-overloadaggressively

#-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-allowaccessmodification
-repackageclasses ''
-renamesourcefileattribute SourceFile
-dontskipnonpubliclibraryclasses

-dontwarn com.fasterxml.jackson.databind.ext.Java7SupportImpl
-keep class com.fasterxml.jackson.databind.ext.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.w3c.dom.**
-dontwarn com.fasterxml.jackson.databind.ext.DOMSerializer

# Strip all Android logging for security and performance
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}