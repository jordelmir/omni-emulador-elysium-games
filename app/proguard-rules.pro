# Elysium Console ProGuard Rules

# Keep Shizuku classes
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Keep JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ElysiumBridge JNI bridge
-keep class com.elysium.console.bridge.ElysiumBridge { *; }

# Keep domain models (used by reflection in some cases)
-keep class com.elysium.console.domain.model.** { *; }

# Compose
-dontwarn androidx.compose.**
