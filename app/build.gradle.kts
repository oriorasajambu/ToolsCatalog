/**
 * The host. Nav graph, DI aggregation, `Application`, `MainActivity` — nothing else.
 *
 * Everything shared with the library modules (compileSdk, Java level, desugaring, Compose, Hilt,
 * Showkase, test wiring) lives in the `minion.android.application` convention so this file and
 * `minion.android.library` cannot drift apart. What stays here is what is genuinely unique to
 * the application: identity, versioning, environment constants, and the module graph.
 */
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

android {
    namespace = "com.minion.scaffold"

    defaultConfig {
        applicationId = "com.minion.scaffold"
        versionCode = 1
        versionName = "1.0"
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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Features are added here as they are created — this is the one place that sees all of them.
    implementation(project(":feature:tools"))
    implementation(project(":feature:qrscan"))
    implementation(project(":feature:qrcreate"))
    implementation(project(":feature:texttools"))

    implementation(libs.androidx.splashscreen)

    testImplementation(project(":core:testing"))
}
