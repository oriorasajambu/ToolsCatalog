/**
 * The host. Nav graph, DI aggregation, `Application`, `MainActivity` — nothing else.
 *
 * Everything shared with the library modules (compileSdk, Java level, desugaring, Compose, Hilt,
 * Showkase, test wiring) lives in the `minion.android.application` convention so this file and
 * `minion.android.library` cannot drift apart. What stays here is what is genuinely unique to
 * the application: identity, versioning, environment constants, and the module graph.
 */
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties

plugins {
    id("minion.android.application")
}

/** Loads a properties file from the repository root, or an empty set if it is absent. */
fun rootProperties(fileName: String): Properties = Properties().apply {
    val file = rootProject.file(fileName)
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * The `BASE_URL` for a flavor, resolved from properties files rather than hard-coded here.
 *
 * Order: an optional per-machine override in `local.properties` (development only), then the
 * flavor's own `<env>.properties` (gitignored, per checkout), then the committed
 * `<env>.properties.template` default so a fresh clone still builds. Missing from all three is a
 * configuration error rather than a silent fallback.
 */
fun baseUrl(envFile: String, allowLocalOverride: Boolean): String {
    if (allowLocalOverride) {
        rootProperties("local.properties").getProperty("BASE_URL")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    for (candidate in listOf(envFile, "$envFile.template")) {
        rootProperties(candidate).getProperty("BASE_URL")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }
    error("BASE_URL is not set. Copy $envFile.template to $envFile, or set BASE_URL in local.properties.")
}

/**
 * Release signing, wired to the `toolbox` keystore at the repository root.
 *
 * Neither the keystore nor its passwords go in VCS: the store/key passwords and the alias are read
 * from `keystore.properties` (gitignored — copy `keystore.properties.template`). When the keystore
 * or its credentials are absent (a fresh clone, or CI without the secrets) the release build is left
 * unsigned so it still assembles, rather than failing at configuration time.
 */
val releaseKeystoreFile = rootProject.file("toolbox")
val keystoreProperties = rootProperties("keystore.properties")
val hasReleaseSigning =
    releaseKeystoreFile.exists() && keystoreProperties.getProperty("storePassword") != null

android {
    namespace = "com.minion.scaffold"

    defaultConfig {
        applicationId = "com.minion.scaffold"
        versionCode = 1
        versionName = "1.0"

        // ONNX Runtime (:feature:ocr's PaddleOCR engine) ships a native library per ABI, and they
        // are large: 26.7MB for arm64-v8a and 19.1MB for armeabi-v7a in the release APK. Only 64-bit
        // ARM is shipped — Play has required 64-bit since 2019 and minSdk is already 29, so a
        // genuinely 32-bit-only device is a small and shrinking slice, and x86 is emulator-only for
        // a camera app. Everything outside this ABI still gets the tool, because the recognizer
        // falls back to ML Kit and says so rather than failing.
        //
        // This is also why the APK did not grow by the full size of what was added: dropping the
        // x86 ABIs shed ML Kit's native libraries for them too.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        // The PP-OCRv5 weights are float32 and deflate to about 87% of their size — roughly 2.8MB
        // saved out of 21.8MB — so storing them costs little and makes first-run extraction a plain
        // copy rather than an inflate of the whole set. Declared here rather than in :feature:ocr
        // because packaging is decided by the application module; the library-level setting is
        // ignored for the final APK.
        noCompress += "onnx"
    }

    // The environment the app talks to is a product flavor, kept separate from the build type: each
    // of `development` and `production` is built both `debug` and `release`, so there are four
    // variants — developmentDebug/Release and productionDebug/Release. The build type stays about
    // how the code is built (debuggable, minified); the flavor is about which backend it points at.
    //
    // :app is the only module that knows the environment. :core:network receives it through the
    // @BaseUrl qualifier and never reads BuildConfig itself — a library module reading the app's
    // BuildConfig is how a "core" module quietly becomes app-specific.
    flavorDimensions += "environment"
    productFlavors {
        create("development") {
            dimension = "environment"
            // A distinct id and label, so a development build installs alongside a production one.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BASE_URL", "\"${baseUrl("dev.properties", allowLocalOverride = true)}\"")
        }
        create("production") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"${baseUrl("prod.properties", allowLocalOverride = false)}\"")
        }
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            configure<CrashlyticsExtension> {
                // A debug build is not minified, so R8 writes no mapping file. Leaving the upload
                // on attaches a task to every debug assemble that looks for a file that is never
                // produced.
                mappingFileUploadEnabled = false
            }

            // TEMPORARY — emulator only, remove when the widget work is verified.
            //
            // defaultConfig ships arm64-v8a alone, which is deliberate and must stay that way for
            // anything released. The consequence is the one CLAUDE.md records: an x86 emulator
            // cannot install the result, so the home-screen widget cannot be looked at on one.
            //
            // abiFilters is a union across defaultConfig, flavour and build type, so naming the
            // emulator ABIs here widens debug builds only and leaves both release variants
            // untouched. Both are listed because a 32-bit x86 system image cannot load an x86_64
            // library; ONNX Runtime and ML Kit publish natives for each, so nothing silently loses
            // its engine.
            ndk {
                abiFilters += setOf("x86", "x86_64")
            }
        }

        release {
            // Signed with the `toolbox` release key when keystore.properties supplies the
            // credentials; otherwise left unsigned (see the signing note above).
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null

            // R8 shrinks and obfuscates. The reflection-driven parts (kotlinx.serialization routes,
            // Gson) are covered by app/proguard-rules.pro; everything else relies on the libraries'
            // own consumer rules.
            //
            // Both flags have been smoke-tested on a signed productionRelease build on a real
            // device — resources (launcher/adaptive icons, splash icon, manifest-referenced XML)
            // all traced as reachable and rendered correctly. Code shrinking found one real bug on
            // that pass, now fixed: see the Gson section below for what broke and why.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            configure<CrashlyticsExtension> {
                // Uploads the R8 mapping so release traces de-obfuscate in the console. Without it
                // every release crash reads as `a.b.c(SourceFile:1)`, which is the state the
                // -keepattributes SourceFile,LineNumberTable in proguard-rules.pro exists to avoid.
                mappingFileUploadEnabled = true
                // The app ships native code (ONNX Runtime), but symbolicating a crash inside it
                // needs the firebase-crashlytics-ndk artifact as well; without that this flag only
                // uploads symbols nothing consumes.
                nativeSymbolUploadEnabled = false
            }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))

    // resolveWidgetRoute matches a widget's tool id against the shipped catalog.
    implementation(project(":core:toolcatalog"))

    // Features are added here as they are created — this is the one place that sees all of them.
    implementation(project(":feature:tools"))
    implementation(project(":feature:qrscan"))
    implementation(project(":feature:qrcreate"))
    implementation(project(":feature:texttools"))
    implementation(project(":feature:weather"))
    implementation(project(":feature:ocr"))
    implementation(project(":feature:level"))
    implementation(project(":feature:soundmeter"))
    implementation(project(":feature:exifstrip"))
    implementation(project(":feature:speedometer"))

    // Not a screen. :app depends on it for two reasons: its receiver has to reach the manifest,
    // and WidgetLaunchIntentFactory has to be bound to something that can name MainActivity.
    implementation(project(":feature:widget"))

    implementation(libs.androidx.splashscreen)

    testImplementation(project(":core:testing"))
}
