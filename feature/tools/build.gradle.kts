/**
 * The home screen: the catalog of tools the app offers.
 *
 * Owns no data layer. The tool list is a static, compile-time registry — a repository, DTO and
 * Retrofit API for a list that ships inside the binary would be ceremony around a `listOf`.
 */
plugins {
    id("minion.android.feature")
}

android {
    namespace = "com.minion.scaffold.feature.tools"
}
