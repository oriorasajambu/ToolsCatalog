import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Base convention for every Android library module.
 *
 * Owns compileSdk / minSdk / Java level / desugaring / test wiring so that twelve modules do not
 * repeat the same forty lines. Modules set only their own `namespace`.
 */

plugins {
    // AGP 9 has built-in Kotlin support; applying 'org.jetbrains.kotlin.android' alongside it is
    // now a hard error. See https://kotl.in/gradle/agp-built-in-kotlin
    id("com.android.library")
}

// Precompiled script plugins get no type-safe `libs` accessor, so the catalog is resolved by hand.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        // Off by default since AGP 8. Enabled so a module can branch on BuildConfig.DEBUG —
        // :core:network uses it to pick an HTTP log level — without :app having to inject a
        // boolean for something the build already knows.
        buildConfig = true
    }
}

dependencies {
    add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk").get())
    add("implementation", libs.findLibrary("kotlin-stdlib").get())
    add("implementation", libs.findBundle("reactive").get())
    add("testImplementation", libs.findBundle("unit-test").get())
    add("androidTestImplementation", libs.findBundle("instrumentation-test").get())
}

/**
 * `check` compiles the instrumented tests, without needing a device.
 *
 * Neither `testDebugUnitTest` nor `assembleDebug` builds `androidTest` sources, so they can stop
 * compiling and nothing says so — which is exactly what happened: `SessionProvider` gained two
 * methods and six instrumented tests in `:core:data` sat unbuildable, with a green board, until
 * someone tried to run them weeks later.
 *
 * Compile only, deliberately. Running them needs a device and belongs in a separate step;
 * catching "this no longer builds" does not, and is where nearly all of the value is.
 *
 * The task is matched by name pattern rather than named outright: a module with product flavors
 * gets one compile task per variant (`compileDevelopmentDebugAndroidTestKotlin`, …) and the
 * unflavored `compileDebugAndroidTestKotlin` never exists there. The collection is live, so it
 * resolves once AGP has registered the variant tasks.
 */
val androidTestCompileTasks = tasks.matching { it.name.matches(Regex("^compile.*DebugAndroidTestKotlin$")) }

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(androidTestCompileTasks)
}
