pluginManagement {
    // Compiles the convention plugins before anything else configures. Everything modular
    // depends on this being here.
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ToolBox"

// The only module that may see both a feature and a repository implementation: it assembles the
// navigation graph and the DI graph, and does nothing else.
include(":app")

// Foundation — pure Kotlin, depends on nothing.
include(":core:common")

// Domain — pure Kotlin. Models, repository interfaces, use cases.
include(":core:domain")

// EMV payload framing, checksums and reference tables. Pure Kotlin, shared by the scan and
// create tools so the CRC has exactly one implementation.
include(":core:emv")

// Wi-Fi credential QR codes. Pure Kotlin, shared by the scan and create tools.
include(":core:wifi")

// Web links. Pure Kotlin.
include(":core:url")

// vCard 3.0 contact cards. Pure Kotlin.
include(":core:vcard")

// Text and developer transforms. Pure Kotlin.
include(":core:text")

// Weather domain shaping — DTO mapping, WMO condition codes, notable-condition thresholds, unit
// conversion. Pure Kotlin, shared by nothing else yet; lives here rather than in the feature only
// because it mirrors every other format module's shape.
include(":core:weather")

// Tilt geometry — the angles a gravity vector implies, the pose machine, the smoothing filter and
// the flip-calibration algebra. Pure Kotlin because a level has no visible ground truth: the only
// way to know the maths is right is to prove it against synthesised vectors in a JVM test.
include(":core:level")

// GNSS measurement shaping — the EGM96 geoid conversion that turns a satellite's ellipsoidal height
// into a height above sea level, the rules that decide when a speed is really zero, and the trip
// accumulators. Pure Kotlin: the geoid is 508 kB of published values whose correct indexing decides
// every altitude the app shows, which can be checked at a hundred thousand points in a JVM test and
// cannot usefully be checked by looking at a phone.
include(":core:gnss")

// Image container surgery — which bytes of a JPEG, PNG or WebP carry metadata and which carry the
// picture. Pure Kotlin, and it never touches a file: it returns a plan of byte ranges to copy, which
// is what makes the "pixels are never re-encoded" guarantee a property of the types rather than of
// whoever reviews the next change.
include(":core:exif")

// Sound level metering — the A/C/Z weighting filters, exponential time weighting, and the session
// accumulator behind Leq. Pure Kotlin for a sharper version of :core:level's reason: a phone cannot
// know its own microphone sensitivity, so the filters are the only part that can be proved — and
// IEC 61672-1 tabulates exactly what they should do.
include(":core:sound")

// Text-recognition shaping — reading-order reconstruction and block assembly. Pure Kotlin; the
// ML Kit types it maps from stay in :feature:ocr, so the ordering algorithm is unit-testable
// without an emulator.
include(":core:ocr")

// Route contracts — pure Kotlin. The only channel through which one feature reaches another.
include(":core:navigation")

// UI
include(":core:designsystem")
include(":core:ui")

// The camera viewfinder, shared by the scan and OCR tools so there is exactly one CameraX setup.
include(":core:camera")

// The tools this app offers, as a table. Shared by the home screen and the home-screen widget,
// which is what keeps it out of either one.
include(":core:toolcatalog")

// Data
include(":core:network")
include(":core:data")

// Test infrastructure, consumed via testImplementation.
include(":core:testing")

// Features
// One line per feature module.
// `python scripts/scaffold_feature.py --name Home` generates the module and prints the line.
include(":feature:tools")
include(":feature:qrscan")
include(":feature:qrcreate")
include(":feature:texttools")
include(":feature:weather")
include(":feature:ocr")
include(":feature:level")
include(":feature:soundmeter")
include(":feature:exifstrip")
include(":feature:speedometer")

// Not a screen: the home-screen App Widget. A module rather than part of :app because it
// carries a manifest component, a provider XML, a DataStore and its own tests, and ":app --
// nothing else" is the rule it would break.
include(":feature:widget")

