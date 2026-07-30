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

rootProject.name = "AndroidScaffold"

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

// Route contracts — pure Kotlin. The only channel through which one feature reaches another.
include(":core:navigation")

// UI
include(":core:designsystem")
include(":core:ui")

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

