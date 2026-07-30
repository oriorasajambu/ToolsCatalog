/**
 * UI tier — tokens and atoms. Knows pixels only.
 *
 * `AppTheme`, colour, type, shape and the dumb widgets built on them. Zero domain imports, so it
 * is reusable in any app. Anything that renders a domain model belongs in `:core:ui`.
 *
 * This is the only module where a raw colour, dimension or font may appear. A hex literal
 * anywhere else is a bug the design system was created to prevent.
 *
 * Showkase comes from the `minion.android.library.compose` convention, along with Compose itself.
 */
plugins {
    id("minion.android.library.compose")
}

android {
    namespace = "com.minion.scaffold.core.designsystem"
}

dependencies {
    // Backs QrCodeImage. `implementation`, not `api`: zxing types appear nowhere in this module's
    // public signatures — the encoder hands back an android.graphics.Bitmap.
    implementation(libs.zxing.core)
}
