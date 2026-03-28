# ============================================================================
# Puber ProGuard/R8 Rules
# ============================================================================

# Preserve source file names and line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================================
# kotlinx.serialization
# ============================================================================
# The serialization compiler plugin generates $serializer classes that must be
# kept. Also keep @Serializable classes' companion objects and their fields.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep serializers referenced via companion.$serializer()
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializer classes
-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }

# Keep the SerialDescriptor for generated serializers
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Ktor
# ============================================================================
# Ktor uses reflection for plugin installation and engine configuration.
# Most consumer rules are shipped, but some gaps exist.

-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**

# ============================================================================
# OkHttp
# ============================================================================
# OkHttp ships consumer rules, but DNS-over-HTTPS needs explicit keep.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-keep class okhttp3.internal.** { *; }

# ============================================================================
# Koin
# ============================================================================
# Koin 4.x uses constructor references via ::ClassName. The constructors of
# all DI-registered classes must be preserved.

-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep constructors for all classes in the app's packages that are
# instantiated via Koin (singleOf, scopedOf, viewModelOf, factoryOf)
-keepclassmembers class com.kino.puber.** {
    public <init>(...);
}

# ============================================================================
# Voyager Navigation
# ============================================================================
# Voyager uses Parcelable for back-stack persistence. Keep Screen implementations.
-keep class cafe.adriel.voyager.** { *; }
-dontwarn cafe.adriel.voyager.**

# ============================================================================
# App: PuberScreen class names (used for ScreenKey via javaClass.simpleName)
# ============================================================================
# PuberScreen.key uses javaClass.simpleName — class names must be preserved.
-keepnames class * implements com.kino.puber.core.ui.navigation.PuberScreen

# Keep the PuberScreen interface itself
-keep class com.kino.puber.core.ui.navigation.PuberScreen { *; }
-keep class com.kino.puber.core.ui.navigation.PuberScreenActivity { *; }

# ============================================================================
# App: Parcelize classes
# ============================================================================
# @Parcelize generates CREATOR at compile time. Keep Parcelable implementations.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class * implements android.os.Parcelable { *; }

# ============================================================================
# Media3 / ExoPlayer
# ============================================================================
# Media3 ships consumer rules, but @UnstableApi usages sometimes need help.
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# ============================================================================
# Coil
# ============================================================================
# Coil 3 ships consumer rules. Keep image decoders/fetchers that are registered
# by class reference.
-keep class coil3.** { *; }
-dontwarn coil3.**

# ============================================================================
# Timber — strip debug logs in release
# ============================================================================
-assumenosideeffects class timber.log.Timber {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}

# Also strip the app's extension functions that wrap Timber
-assumenosideeffects class com.kino.puber.core.logger.LoggerKt {
    public static void log(...);
}

# ============================================================================
# Kotlin / Coroutines
# ============================================================================
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep Kotlin Metadata for reflection used by serialization
-keepattributes RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }

# ============================================================================
# Compose
# ============================================================================
# Compose is handled by the AGP Compose compiler plugin; no custom rules needed.
# But keep @Composable lambdas from being removed if referenced indirectly.
-dontwarn androidx.compose.**

# ============================================================================
# Security Crypto
# ============================================================================
# AndroidX Security Crypto uses reflection for KeyStore/Cipher providers
-keep class androidx.security.crypto.** { *; }

# ============================================================================
# General safety
# ============================================================================
# Keep enum values (used in serialization, switch statements, etc.)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations
-keepattributes Signature
-keepattributes Exceptions
