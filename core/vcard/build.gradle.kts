/**
 * vCard 3.0 contact cards — RFC 2426. Pure Kotlin, zero Android.
 *
 * The substance here is the text format: escaping `\ ; ,` and newlines inside values, the five
 * structured components of `N`, and unfolding lines that other generators wrapped at 75 octets.
 * Each of those, done wrong, produces a card that scans and holds the wrong thing.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
}
