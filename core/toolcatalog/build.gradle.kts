/**
 * The tool catalog: what this app offers, as it ships in the binary.
 *
 * An Android library rather than part of `:core:domain`, because an entry carries an `ImageVector`,
 * a `@DrawableRes` and two `@StringRes` — none of which exist on `minion.jvm.library`'s classpath.
 * Splitting a pure-Kotlin id/route half from an Android presentation half was considered and
 * rejected: it needs a sealed `ToolId` and an exhaustive `when` to stay complete, which is more
 * machinery than a fourteen-entry table earns.
 *
 * Not `:core:designsystem` either. That module owns tokens and dumb widgets; making it depend on
 * `:core:navigation` and know which features exist is the drift the module rules exist to prevent.
 *
 * Consumed by `:feature:tools` (the home screen) and `:feature:widget` (the home-screen widget),
 * which is what makes it shared rather than one feature's private concern.
 */
plugins {
    id("minion.android.library.compose")
}

android {
    namespace = "com.minion.scaffold.core.toolcatalog"
}

dependencies {
    // `api`, not `implementation`: AppRoute appears in ToolDescriptor's own public signature, so
    // every consumer needs it on their compile classpath to read `descriptor.route`.
    api(project(":core:navigation"))
}
