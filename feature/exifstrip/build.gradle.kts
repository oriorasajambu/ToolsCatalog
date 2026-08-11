/**
 * EXIF metadata stripper: show what a photo reveals, then hand back a clean copy.
 *
 * The container surgery lives in `:core:exif`, where it can be proved against files built byte by
 * byte in a JVM test. What is here is only the parts that cannot be pure: reading tags through
 * `ExifInterface`, executing a plan against real streams, the cache and share plumbing, and the UI.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.exifstrip"
}

dependencies {
    implementation(project(":core:exif"))

    // Reads the tag values for display. Deliberately *not* used to strip: it understands the tags it
    // knows about, so XMP, IPTC, maker notes and MPF blocks would survive a save — see the module
    // KDoc on ExifTagReader.
    implementation(libs.androidx.exifinterface)

    // Stores the colour-profile preference. Feature-local rather than :core:data — only this feature
    // reads it, and the repo promotes on the second consumer.
    implementation(libs.data.store)
}
