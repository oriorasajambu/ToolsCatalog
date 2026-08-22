/**
 * The home-screen App Widget: a strip of up to five tools, configured in-app.
 *
 * A feature module is a mild stretch — this draws no screen. It is still the right home: the widget
 * carries a manifest component, an appwidget-provider XML, a DataStore and its own tests, and
 * CLAUDE.md's rule for `:app` is "nothing else". The alternative, a `:core:` module owning a
 * manifest receiver, is worse.
 *
 * The configuration screen is deliberately *not* here. It lives in `:feature:tools` beside the
 * catalog it edits, which is why the pieces both need sit in `:core:data` — one feature may not
 * depend on another.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.widget"
}

dependencies {
    // The catalog the widget renders, and the pinned-list model it renders through.
    implementation(project(":core:toolcatalog"))

    // Not granted by minion.android.feature, deliberately — see CLAUDE.md's promotion rule.
    implementation(project(":core:data"))

    implementation(libs.data.store)
    implementation(libs.bundles.glance)
}
