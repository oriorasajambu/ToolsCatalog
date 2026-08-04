# ============================================================================================
# Project R8 / ProGuard rules, applied on top of proguard-android-optimize.txt for release builds.
#
# Most dependencies ship their own consumer rules (Hilt/Dagger, Jetpack Compose, Retrofit, OkHttp,
# Coil, ML Kit), so R8 already knows how to keep them. What is left is the reflection-driven code
# this app owns: kotlinx.serialization (type-safe navigation routes) and Gson (Retrofit's JSON
# converter). Keep this file to those concerns rather than blanket `-keep`s, or minification stops
# shrinking anything.
# ============================================================================================

# --- Attributes: generics, annotations, and readable release crash traces -------------------
-keepattributes Signature
-keepattributes InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
# Keep line numbers so release stack traces stay usable; hide the original file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ------------------------------------------------------------------
# Type-safe Navigation encodes every route through its generated serializer. If R8 strips those,
# navigating crashes at runtime, so keep the serializer machinery for @Serializable types. These
# are the serialization library's own recommended rules (also shipped as consumer rules; repeated
# here so the intent is explicit and survives a consumer-rule regression).

# Keep the `Companion` of every @Serializable class — serializer lookup goes through it.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers public class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects — the app's `data object` routes.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# The navigation routes are the concrete serializable types the graph reconstructs. Keep them
# whole so their generated $$serializer and their fields survive obfuscation.
-keep class com.minion.scaffold.core.navigation.** { *; }

# --- Gson (Retrofit converter-gson) ---------------------------------------------------------
# Gson reads model fields reflectively, so any class it (de)serialises must keep its field names.
# There are no network DTOs yet; when you add them, keep the model package explicitly, e.g.:
#     -keep class com.minion.scaffold.**.dto.** { <fields>; <init>(...); }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# --- ML Kit barcode scanning ----------------------------------------------------------------
# ML Kit finds its internal components by Class.forName()-ing registrar names embedded as strings
# in the merged manifest (<meta-data> under MlKitComponentDiscoveryService). R8 renaming or removing
# those classes leaves the scanner half-wired, and building a BarcodeScanner then NPEs on a null
# internal component. Keep every ComponentRegistrar so it survives under its original name; the
# components each registrar constructs are traced and kept from there.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.mlkit.common.internal.CommonComponentRegistrar { *; }
-keep class com.google.mlkit.vision.barcode.internal.BarcodeRegistrar { *; }
-keep class com.google.mlkit.vision.common.internal.VisionCommonRegistrar { *; }

# --- OkHttp: optional runtime providers it references but does not require -------------------
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
