# Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}

-repackageclasses
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute SourceFile

# Keep Xposed entry point
-keep class com.redwind.hyperorig.hook.HookEntry { *; }

# Keep all hook classes (referenced by name in Xposed framework)
-keep class com.redwind.hyperorig.hook.** { *; }

# Keep Parcelable data classes (used in broadcast extras)
-keep class com.redwind.hyperorig.utils.miuiStrongToast.data.** { *; }

# Keep ConfigManager (used via reflection)
-keep class com.redwind.hyperorig.config.** { *; }

# Keep RfcommController (referenced by hooks)
-keep class com.redwind.hyperorig.pods.** { *; }

# Keep FocusIslandUtil (called from hooks)
-keep class com.redwind.hyperorig.utils.FocusIslandUtil { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.redwind.hyperorig.**$$serializer { *; }
-keepclassmembers class com.redwind.hyperorig.** {
    *** Companion;
}
-keepclasseswithmembers class com.redwind.hyperorig.** {
    kotlinx.serialization.KSerializer serializer(...);
}
