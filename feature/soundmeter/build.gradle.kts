/**
 * Sound meter — how loud is it here, and what was the loudest it got.
 *
 * The maths lives in `:core:sound`, where it can be proved against IEC 61672-1's published response
 * tables. What is here is only the parts that cannot be pure: the `AudioRecord` bridge and the
 * source-selection chain that keeps automatic gain control out of the signal, the monitor that
 * notices when another app takes the microphone, the preference store, and the UI.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.soundmeter"
}

dependencies {
    implementation(project(":core:sound"))

    // Stores the calibration offset and the two weighting modes. Feature-local rather than
    // :core:data — only this feature reads it, and the repo promotes on the second consumer.
    implementation(libs.data.store)
}
