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

dependencies {
    // The catalog moved out so the home-screen widget could read it too. `api`, not
    // `implementation`: ToolDescriptor appears in ToolsState's and ToolsIntent's public signatures.
    api(project(":core:toolcatalog"))
}
