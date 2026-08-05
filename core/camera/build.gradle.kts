/**
 * The camera viewfinder, shared by `:feature:qrscan` and `:feature:ocr`.
 *
 * Extracted when OCR became a second camera consumer — the repo promotes on the second consumer,
 * not in anticipation of one. What lives here is everything that is true of *any* viewfinder:
 * the CameraX controller setup, lifecycle binding, torch, zoom, tap-to-focus and the coordinate
 * transform. What a particular tool draws on top, and what it does with the frames, stays in that
 * tool.
 *
 * CameraX is declared here rather than in the feature convention plugin for the same reason it was
 * declared in `:feature:qrscan` before: two consumers do not justify putting it on every future
 * feature's classpath.
 */
plugins {
    id("minion.android.library.compose")
}

android {
    namespace = "com.minion.scaffold.core.camera"
}

dependencies {
    implementation(libs.bundles.camera)

    // `Task.await()` appears in this module's public surface, so `api` rather than
    // `implementation` — the Hilt component and both consumers need the type on their classpath.
    api(libs.play.services.tasks)
}
