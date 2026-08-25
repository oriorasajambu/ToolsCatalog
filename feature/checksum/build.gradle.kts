/**
 * The checksum verifier: hash some text, and check the digest against one you were given.
 *
 * The whole module is a Compose shell over one use case that already existed. `:core:text`'s
 * `TransformTextUseCase` computes the digest — the same instance type `:feature:texttools` uses for
 * its transform screen — so nothing here reimplements MD5, SHA-1 or SHA-256, and no new module,
 * no new Hilt binding and no change to `:core:text` was needed to reuse it.
 *
 * Everything else comes from the `minion.android.feature` convention. Its entire public surface is
 * `NavGraphBuilder.checksumScreen()`; everything else is `internal`.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.checksum"
}

dependencies {
    // The one dependency this feature declares for itself: the module that already owns hashing.
    implementation(project(":core:text"))
}
