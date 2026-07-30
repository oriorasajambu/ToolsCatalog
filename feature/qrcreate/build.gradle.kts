/**
 * The EMV QR authoring tool: a form in, a scannable payload out.
 *
 * The EMV rules live in `:core:emv` and the QR rendering in `:core:designsystem`, so what this
 * module owns is the form, its validation feedback and the export paths. It writes payloads the
 * scan tool reads, without either feature knowing the other exists.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.qrcreate"
}

dependencies {
    implementation(project(":core:emv"))
    implementation(project(":core:wifi"))
    implementation(project(":core:url"))
    implementation(project(":core:vcard"))
}
