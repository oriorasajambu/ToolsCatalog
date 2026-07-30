/**
 * Wi-Fi credential QR codes — the `WIFI:T:…;S:…;P:…;;` format. Pure Kotlin, zero Android.
 *
 * A core module rather than a package inside the create feature, because the scanner reads this
 * format as well as writing it, and a feature may not depend on another feature. Two consumers is
 * the same threshold `:core:emv` was extracted at.
 *
 * The escaping rules are the substance here: an SSID containing a semicolon or one that looks
 * like hexadecimal produces a code no phone can read if written naively, and neither case is rare.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
