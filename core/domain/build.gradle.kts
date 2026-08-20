/**
 * Domain tier. Pure Kotlin — zero Android, zero Compose.
 *
 * Models, repository interfaces and use cases. The repository interfaces are declared here and
 * implemented in `:core:data`, so the dependency points inward: the domain never learns where
 * its data comes from.
 *
 * The absence of the Android plugin is the boundary rule made mechanical: a stray `import
 * android.*` here fails to compile rather than surviving to code review.
 */
plugins {
    id("minion.jvm.library")
}

dependencies {
    // api, not implementation: use cases return AppResult<T> and repository interfaces are
    // declared in terms of it, so AppResult and DomainError are part of this module's own
    // public signatures.
    api(project(":core:common"))

    // api for the same reason: FeatureFlagRepository returns a Flow, so kotlinx.coroutines is part
    // of this module's own public signatures. The jvm.library convention already adds it as
    // `implementation`, which puts it on this module's compile classpath but not its consumers'.
    api(libs.coroutines.core)
}
