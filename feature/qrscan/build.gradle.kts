/**
 * The EMV QR inquiry tool: scan or paste a payload, read it back decoded.
 *
 * Owns the camera, the gallery decoder and the report UI. The EMV domain itself lives in
 * `:core:emv`, shared with the create tool.
 *
 * CameraX and ML Kit are declared here rather than in `minion.android.feature`, because this is
 * the only module that scans anything and a convention plugin would put them on every future
 * feature's classpath.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.qrscan"
}

dependencies {
    implementation(project(":core:emv"))
    implementation(project(":core:wifi"))
    implementation(project(":core:url"))
    implementation(project(":core:vcard"))

    implementation(libs.bundles.camera)
    implementation(libs.mlkit.barcode)
    // zxing is not here: encoding a QR for display moved to :core:designsystem, which both tools
    // draw from. This module only ever reads codes.
}
