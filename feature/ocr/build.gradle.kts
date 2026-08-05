/**
 * On-device OCR: point the camera at text, or pick a photo, and get the text out.
 *
 * Runs entirely on-device through ML Kit's **bundled** Latin model — no network and no Play
 * Services runtime, which keeps the tool consistent with the app's offline positioning. The
 * viewfinder comes from `:core:camera` and the reading-order reconstruction from `:core:ocr`; what
 * lives here is the ML Kit binding, the block-selection UI and the MVI plumbing.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.ocr"
}

dependencies {
    implementation(project(":core:camera"))
    implementation(project(":core:ocr"))

    implementation(libs.bundles.camera)
    implementation(libs.mlkit.text.recognition)

    // Bitmap decoding reads EXIF to find how the photographer was holding the phone.
    implementation(libs.androidx.exifinterface)

    // The recognition-engine choice. Feature-local rather than :core:data — only this feature
    // reads it, and the repo promotes to a core module on the second consumer, not the first.
    implementation(libs.data.store)
}
