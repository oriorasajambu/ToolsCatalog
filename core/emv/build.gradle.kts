/**
 * The EMV Merchant Presented Mode domain. Pure Kotlin — zero Android, zero Compose.
 *
 * Framing, checksums, the tag catalog and the ISO reference tables, plus the use cases that read
 * and write a payload. Extracted from `:feature:qrscan` when a second feature needed to *build*
 * what that one reads: the alternative was a second copy of the CRC, and a fix to one copy
 * silently not reaching the other is precisely the failure this code exists to detect.
 *
 * Deliberately not registered in `minion.android.feature`. This is a bounded subdomain, not
 * vocabulary every future feature should see, so the two consumers depend on it by name.
 *
 * The parser internals (`EmvTlvParser`, `EmvCrc16`, `EmvTagCatalog`) stay `internal`: no consumer
 * frames a TLV segment or computes a checksum directly, they call a use case.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    // The use cases are @Inject-constructed. Not `:core:common` — this module has its own
    // EmvParseResult and never touches AppResult, so depending on it would buy only this one
    // annotation.
    implementation(libs.javax.inject)
}
