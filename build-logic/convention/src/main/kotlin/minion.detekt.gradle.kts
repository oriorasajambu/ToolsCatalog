/**
 * Static analysis via detekt.
 *
 * Applied through the base module conventions (`minion.android.application`,
 * `minion.android.library`, `minion.jvm.library`) rather than requested by each module — the
 * same reason Showkase rides along on `minion.android.library.compose` instead of being declared
 * per feature.
 *
 * One `config/detekt/detekt.yml` at the repo root, not per module: a rule that means something
 * different in `:core:gnss` than in `:feature:qrscan` is a rule that should not exist yet.
 *
 * `buildUponDefaultConfig = false`, deliberately: detekt ships roughly 150 rules active by
 * default, and turning all of them on at once against a 31-module codebase that has never run
 * detekt would produce a wall of unreviewed findings rather than a usable first pass. Only the
 * rules the config file lists explicitly are active; the set grows one deliberate addition at a
 * time.
 *
 * Two things are held out of the source set, both because the rules would be judging code against
 * a standard it was never written to:
 *
 *  - **`src/test`.** A test's backtick name is its documentation, so the public-API doc guards
 *    fire on every `@Test` function in the repo — 166 of them — while saying nothing a reader of
 *    the test would want. The algorithm modules carry the bulk of the suite, so this is most of
 *    the noise for none of the signal.
 *  - **`vendor/`.** The PaddleOCR sources under `:feature:ocr` are third-party code kept close to
 *    upstream on purpose. Restyling them to house conventions is what makes the next re-vendor a
 *    merge conflict rather than a copy.
 */

import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
}

tasks.withType<Detekt>().configureEach {
    // The plugin's default source set spans main *and* test; narrowing it to main is what drops
    // `src/test`. A module without Kotlin sources reports NO-SOURCE and is skipped.
    setSource(files("src/main/java", "src/main/kotlin"))
    exclude("**/vendor/**")
}

// Not wired in by the detekt Gradle plugin on its own — `check` failing on a real finding is what
// makes this an enforced convention rather than a task nobody remembers to run, the same reasoning
// behind the androidTest-compile guard elsewhere in build-logic.
tasks.matching { it.name == "check" }.configureEach {
    dependsOn(tasks.named("detekt"))
}
