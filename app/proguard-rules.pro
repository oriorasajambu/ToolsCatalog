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

# --- Gson (Retrofit converter-gson, and any other Gson round-trip such as a Room cache blob) ----
# Gson reads model fields reflectively, so any class it (de)serialises must keep its field names —
# this rule protects exactly the fields annotated @SerializedName, wherever they are.
#
# THE ANNOTATION IS THE KEEP RULE. A Gson-(de)serialised class with no @SerializedName fields is
# invisible to this rule and gets no protection at all. This bit a real release build once: the
# weather feature's on-disk forecast cache (`CachedForecast` and friends, `feature/weather/.../
# data/local/CachedForecast.kt`) round-trips through plain Gson with no @SerializedName, R8 struck
# a nested generic field's Signature attribute since nothing told it to keep it, and every cached
# forecast came back `ClassCastException: LinkedTreeMap cannot be cast to CachedHourlyEntry` — only
# in a signed release build; debug builds don't minify, so nothing caught it until one was run on a
# device. Every Gson-facing class, DTO or otherwise, needs @SerializedName on every field.
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
# Text recognition, same mechanism. The wildcard above already covers these; they are named for the
# same reason the barcode ones are — so the intent survives a consumer-rule regression, and so a
# reader can see which detectors this app actually depends on.
-keep class com.google.mlkit.vision.text.internal.TextRegistrar { *; }
-keep class com.google.mlkit.vision.text.bundled.common.internal.BundledTextRegistrar { *; }

# --- ONNX Runtime: the PaddleOCR engine ------------------------------------------------------
# The native library resolves these classes and their fields from JNI, so R8 sees no reference to
# them from Kotlin and is free to rename or strip them. It presents as an UnsatisfiedLinkError or a
# NoSuchMethodError the first time a session is created — release-only, and invisible in debug,
# which is the same shape as the two release bugs this file already documents.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# --- Firebase / Crashlytics ------------------------------------------------------------------
# The Firebase SDKs ship consumer rules, and the ComponentRegistrar keep above (written for ML Kit)
# already covers Firebase's own registrars — the two libraries share that discovery mechanism.
# What is left is Crashlytics' de-obfuscation contract: it symbolicates a release trace by matching
# it against the uploaded mapping file, which only works if the frames still carry a file and a
# line. `-keepattributes SourceFile, LineNumberTable` at the top of this file supplies both, so do
# not remove it while Crashlytics is in the build.
#
# The Crashlytics build tools reflect over their own generated build-id class, and the SDK reads
# some model fields reflectively when writing a report.
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# --- OkHttp: optional runtime providers it references but does not require -------------------
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
