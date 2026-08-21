# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An opinionated Android scaffold: Kotlin, Jetpack Compose, MVI, Clean Architecture, Hilt, and a
Gradle multi-module graph whose boundaries are enforced by the build rather than by code review.
The repo currently carries a worked example app, **ToolBox** (`app_name` in strings.xml) — an
offline utility app whose tools cover scanning/building EMV, Wi-Fi, link and vCard QR codes, text
transforms, a weather lookup, on-device OCR, a bubble level, a sound-level meter and an EXIF
stripper. Package root is `com.minion.scaffold`.

The full architectural rationale (why each rule exists, not just what it is) lives in
[README.md](README.md) — read it before making structural changes; this file summarizes what's
needed to work productively day to day.

## Commands

```bash
./gradlew build                        # compile everything, unit tests, compile androidTest, lint
./gradlew testDebugUnitTest             # unit tests only, all modules
./gradlew :feature:tools:testDebugUnitTest              # unit tests for one module
./gradlew :feature:tools:testDebugUnitTest --tests "*.HomeViewModelTest"   # single test class
./gradlew :feature:tools:testDebugUnitTest --tests "*.HomeViewModelTest.loads on init"  # single test
./gradlew assembleDevelopmentDebug     # build one variant (see Build variants below)
./gradlew :app:signingReport            # confirm release signing is wired up
python scripts/scaffold_feature.py --name Home   # generate a new feature slice (see below)
```

There is no separate lint-only or ktlint task beyond what `./gradlew build` runs; Android Lint
runs as part of `build`. `check` is wired to also compile `androidTest` sources on every module so
androidTest can't silently rot even though nothing normally builds it. The dependency is matched by
name pattern (`compile*DebugAndroidTestKotlin`) rather than named outright — a module with product
flavors gets one task per variant, so `:app` has `compileDevelopmentDebugAndroidTestKotlin` and
`compileProductionDebugAndroidTestKotlin` and never the unflavored name. Naming it directly made
`./gradlew build` fail outright while resolving `:app:check`.

First-time setup: copy `keystore.properties.template` → `keystore.properties` and
`dev.properties.template`/`prod.properties.template` → `dev.properties`/`prod.properties` if you
need real values; the repo builds without them (unsigned release, template `BASE_URL`).

## Module graph

```
:app                    Application, MainActivity, NavHost, DI aggregation — nothing else
├── :core:common         Pure Kotlin. AppResult, DomainError, dispatcher qualifiers, UiState/UiIntent/UiEffect markers.
├── :core:domain         Pure Kotlin. Shared models, repository interfaces, use cases.
├── :core:navigation     Pure Kotlin. @Serializable route contracts — the only channel between features.
├── :core:designsystem   AppTheme, colour/type/shape tokens, dumb widgets.
├── :core:ui             MviViewModel, ObserveAsEvents, DomainError → @StringRes, PermissionState.
├── :core:camera         CameraX viewfinder — torch, zoom, tap-to-focus, still capture. Shared by qrscan and ocr.
├── :core:network        Shared OkHttp/Retrofit, safeCall, error mapping.
├── :core:data           Data shared BETWEEN features (not a feature's own data layer).
├── :core:testing        MainDispatcherRule, fakes — testImplementation only.
├── :core:emv/:wifi/:url/:vcard/:text   Pure-Kotlin domain logic for each QR/text format.
├── :core:weather        Pure Kotlin. WMO codes, unit conversion, notable-condition thresholds.
├── :core:ocr            Pure Kotlin. Reading-order reconstruction, line→block grouping, OcrEngine.
├── :core:level          Pure Kotlin. Tilt geometry, pose machine, gravity smoothing, flip calibration.
├── :core:sound          Pure Kotlin. A/C/Z weighting filters, time weighting, the Leq session accumulator.
├── :core:exif           Pure Kotlin. JPEG/PNG/WebP container surgery — returns byte-range strip plans, never touches a file.
└── :feature:*           tools, qrscan, qrcreate, texttools, weather, ocr, level, soundmeter, exifstrip — one per screen area.
```

Dependency rules (enforced by convention plugins, not review):

```
:app ──► :feature:*  ──►  :core:*
:feature:a  ✗  :feature:b        never — route through :core:navigation
:core:*     ✗  :feature:*        never
:core:common depends on nothing
```

- `:core:common`, `:core:domain`, `:core:navigation` and the QR/text format modules apply
  `minion.jvm.library` (plain Kotlin, no Android plugin) — `import android.*` is a compile error
  there, not a review comment.
- Everything in a feature module is `internal` except its navigation entry point
  (`fun NavGraphBuilder.homeScreen(...)`). Without `internal`, `:app` could construct a
  ViewModel directly and the boundary becomes decorative.
- Hilt bindings live beside the implementations they bind, in each feature's own `internal
  @Module` — not centralized, since a central `:core:di` would have to depend on every feature.
- Promote something into `:core:data` or `:core:domain` only once **two** features need it; one
  feature needing it is that feature's own concern.

## Convention plugins (`build-logic/`)

An included build compiled before the rest of the project configures. Module `build.gradle.kts`
files apply exactly one plugin and set a namespace; everything else is centralized.

| Plugin | Applies to | Provides |
|---|---|---|
| `minion.android.application` | `:app` | Android app plugin, SDK levels, desugaring, Compose, Hilt, Showkase, Firebase, test wiring |
| `minion.android.library` | Android library modules | same, minus Compose, plus the androidTest-compile guard |
| `minion.android.library.compose` | modules that draw | Compose BOM + bundles, Showkase processor |
| `minion.android.hilt` | data-layer modules | Hilt + KSP without Compose |
| `minion.android.feature` | every `:feature:*` | Compose + Hilt + navigation + the core modules a feature may see |
| `minion.jvm.library` | pure-Kotlin modules | `kotlin-jvm` only, no Android plugin |

Dependency versions live in `gradle/libs.versions.toml`, shared by the main build and `build-logic`.

## Build variants & environment

Two flavors (`development`/`production`, the `environment` dimension) × two build types
(`debug`/`release`). `development` gets a `.dev` applicationId suffix, a `-dev` version suffix,
and a DEV-badged launcher icon so it installs alongside production. `release` runs R8
(`app/proguard-rules.pro`) and is signed with the `toolbox` keystore at the repo root.

`BASE_URL` resolves per flavor, first hit wins: `local.properties` (development-only override) →
`dev.properties`/`prod.properties` (gitignored) → `*.template` (committed fallback). Only `:app`
reads it; `:core:network` receives it via the `@BaseUrl` qualifier, never `BuildConfig`.

## The MVI contract

Every feature declares exactly three types in `presentation/`:

- **State** (`UiState`) — flat data class the UI renders, safe defaults for every field. Put
  mutually exclusive phases (loading/success/empty/failure) in a nested `ContentState` sealed
  interface rather than sibling booleans, so "loading = true with a non-empty list" is
  unrepresentable.
- **Intent** (`UiIntent`) — sealed interface of everything the user can do, so `onIntent`'s `when`
  is exhaustive with no `else`.
- **Effect** (`UiEffect`) — one-shot events (toast, snackbar, navigation) sent over a buffered
  `Channel`, never replayed after rotation. Ask "would replaying this after a rotation be wrong?"
  to decide State vs Effect.

`MviViewModel` (`:core:ui`) owns the `MutableStateFlow` and the `Channel`; subclasses only get
`reduce { }`, `currentState`, and `emitEffect()` — no screen writes state directly. Collect
effects with `ObserveAsEvents` (`:core:ui`), never `LaunchedEffect(Unit) { flow.collect {} }` —
the latter is scoped to composition and drops events fired while the screen is backgrounded;
`ObserveAsEvents` uses `repeatOnLifecycle(STARTED)` and the buffered channel holds events until
resume.

## Error handling

Repositories and use cases return `AppResult<T>` (`Success`/`Failure(DomainError)`), never
`kotlin.Result`, never a raw `Throwable`.

```
Exception ──► toDomainError() ──► DomainError ──► toMessageRes() ──► stringResource
   :core:network                    everywhere              :core:ui        the composable
```

- `safeCall { }` (`:core:network`) is the exception boundary — it rethrows
  `CancellationException` before catching `Throwable`; a plain `runCatching` would turn a
  cancelled screen's request into a spurious failure.
- `DomainError` is sealed — a new failure mode becomes a compile error everywhere it must be
  handled. `e.message` never reaches state (developer text, often `null`, never localized); only
  the composable turns an error into words via `stringResource(error.toMessageRes())`.
- A feature's own errors do not extend `DomainError` (a sealed interface can only be extended
  from the same package+module). Declare a feature-local sealed type, return it in the success
  channel when it's a domain rejection rather than a failure, and map it to a `@StringRes` in the
  feature itself.

## Adding a feature

Prefer `python scripts/scaffold_feature.py --name Home` (flags: `--dry-run`, `--force`,
`--no-remote`, `--no-tests`, `--plural People`) — it writes contract, ViewModel, screen with
previews, navigation entry point, domain model, repository interface + impl, use case, DTO,
Retrofit API, mapper, two Hilt modules, strings, and a passing ViewModel test, then prints the
three manual wiring lines still needed:

1. `settings.gradle.kts` — `include(":feature:home")`
2. `app/build.gradle.kts` — `implementation(project(":feature:home"))`
3. `app/.../navigation/AppNavHost.kt` — `homeScreen(onNavigateToDetail = { … })`

Layers are packages (`presentation/`, `domain/`, `data/`, `di/`) inside one feature module, not
separate Gradle modules — split into submodules only when different teams own the layers. Build
order that avoids rework: contract → domain → data → DI → ViewModel → screen → navigation → tests.
Routes belong in `:core:navigation`, never the feature itself, so other features can navigate to
it without depending on the module. Pass navigation down as lambdas — never hand a feature the
`NavController`.

## UI tokens — no literals in a screen

Nothing a screen draws is written as a literal. Four kinds of value, four homes, no exceptions:

| Value | Belongs in | Read with |
|---|---|---|
| Any user-visible text | the feature's `res/values/strings.xml` | `stringResource(R.string.x)` |
| Any `dp` / `sp` — padding, size, gap, radius, border, thickness | the feature's `res/values/dimens.xml` | `dimensionResource(R.dimen.x)` |
| Any colour | `MaterialTheme.colorScheme` | the scheme, never `Color(0xFF…)` |
| Any `TextStyle` — including `letterSpacing`, `fontSize`, `fontWeight` | `:core:designsystem`'s `AppTypography` or `AppTextStyles` | `MaterialTheme.typography.x` / `AppTextStyles.x` |

The rules that are easy to get wrong:

- **`1.dp` is not too small to tokenize.** A border width repeated at three call sites is three
  values that drift the moment one card gets nudged; `tools_border` is one. The same goes for a
  `2.dp` scanline and a `6.dp` gap — "it's only used here" is how the second use gets pasted in.
- **Derive small spacings, don't re-type them.** `:feature:weather` keeps a single
  `weather_spacing` and writes `spacing / 2` and `spacing / 4`; that is why the whole forecast
  re-spaces from one number. Pass the `Dp` down to private composables rather than letting a leaf
  reach for `8.dp` because it wasn't given one.
- **Never widen a shared Material type slot to suit one screen.** `labelMedium` and `labelLarge`
  have two dozen call sites, so a `letterSpacing` baked into the slot re-tracks all of them. A
  treatment the Material scale has no name for — an eyebrow, a section heading — becomes a new
  entry in `AppTextStyles`, not a `.copy(letterSpacing = 1.6.sp)` at the call site.
- **A fixed colour needs a named `private val` and a reason.** Some colours genuinely answer to
  something other than the palette — a QR quiet zone answers to the scanner, camera-overlay
  colours answer to the image behind them (see the note in *Things that will bite you*). Those are
  legitimate, but inline they read as an oversight. Hoist to `private val QR_QUIET_ZONE =
  Color.White` with the one-line reason, the way `ScanReticle` and `BlockOverlay` already do.
- **String interpolation in a composable is hardcoded text.** `"${temp.roundToInt()}°"` and
  `"$day - $date"` look like formatting, not copy — but the degree mark, the separator and their
  placement are all translatable. Use a `%1$d`/`%1$s` template in `strings.xml`.

A literal that survives review because it "isn't user-facing" or "is only a number": `testTag`
identifiers, log tags, and `@Preview` sample data are genuinely exempt, and so is `0.dp`. Nothing
else is.

## Testing

`:core:testing` re-exports JUnit, MockK, Turbine and `kotlinx-coroutines-test` as `api`, added
automatically by the feature convention plugin. `MainDispatcherRule` defaults to
`StandardTestDispatcher` (coroutines queue, tests drive them with `advanceUntilIdle()`) —
deliberately not `UnconfinedTestDispatcher`, which would hide ordering bugs by making everything
look synchronous. Assert typed `DomainError` values, never message substrings.

## Renaming this scaffold for a new project

Everything is under `com.minion.scaffold`; see [README.md § 7](README.md) for the full checklist
(package rename, `settings.gradle.kts` root name, `applicationId`, `app_name`/theme name,
designsystem palette + launcher icons, `dev.properties`/`prod.properties`, keystore).

## Firebase

Analytics, Crashlytics and Remote Config, applied only by `minion.android.application` — the config
is `app/google-services.json`, which is application identity, so no library module may depend on
it. Versions come entirely from the Firebase BOM (`libs.versions.toml`); the product artifacts are
declared without one. `FirebaseModule` (`app/.../di/`) binds the three singletons so callers take
them as constructor parameters instead of reaching for `Firebase.analytics`, and seeds Remote Config
from `app/src/main/res/xml/remote_config_defaults.xml` — every key the console serves belongs in
that file too, or it reads as `""`/`0`/`false` before the first fetch.

Collection is off in debug builds (`<meta-data>` in `app/src/debug/AndroidManifest.xml`); release
builds keep the SDK defaults. The release build type uploads its R8 mapping, which is why
`-keepattributes SourceFile, LineNumberTable` in `app/proguard-rules.pro` must stay.

## Things that will bite you

- **The configuration cache does not see `keystore.properties`, `dev.properties` or
  `prod.properties`.** `app/build.gradle.kts` reads all three through `java.util.Properties` at
  configuration time, which the cache cannot track as an input. So editing `dev.properties` and
  rebuilding gives you the *previously cached* `BASE_URL`, with no warning and no failure — the app
  just talks to the old backend. After changing any of those three files, either run once with
  `--no-configuration-cache`, or invalidate the cache. The real fix is reading them through
  `providers.fileContents(...).asText`, which the cache does track; until that lands, this is a
  live trap.
- `@ShowkaseRoot` must live in `src/main`, not `src/debug` — KSP doesn't scan the debug source
  set, so it silently generates nothing there.
- A `@Preview` Showkase should catalog must be `internal`, not `private` (Showkase can't call a
  private function); the compose convention sets `skipPrivatePreviews=true` so a stray private
  preview just doesn't show up rather than failing the build.
- A type returned from a `@Provides` method must be exposed as `api`, not `implementation` — the
  Hilt component is generated in `:app`, so the type must be on `:app`'s compile classpath. This
  is why `:core:network`'s bundle is `api` and why Chucker is built inside
  `provideOkHttpClient()` instead of being provided directly (keeps it off every consumer's
  classpath).
- Use `LocalResources.current`, not `LocalContext.current.getString()` — a `Context` read isn't
  invalidated by a config change, so text goes stale after a locale switch; lint fails the build
  on this.
- Give every `LazyColumn` item a stable `key` — without one, inserting at the top recomposes and
  repositions every row.
- Default Gradle dependencies to `implementation`; use `api` only when a type appears in the
  module's own public signatures.
- Anything drawn over the camera preview needs **fixed** colours, not theme ones. A theme colour
  answers to the palette rather than to the image behind it: the scan hint used `inverseOnSurface`,
  which is dark in the dark theme, and was invisible against the reticle's dark scrim. `ScanReticle`
  and the OCR `BlockOverlay` both record this.
- `git clone` needs **git-lfs**. The PP-OCRv5 weights under `feature/ocr/src/main/assets/` are LFS
  objects; without it you get pointer files, PaddleOCR fails to start, and the app silently falls
  back to ML Kit with a notice rather than telling you your checkout is incomplete.
- `:app` sets `abiFilters` to **arm64-v8a only** — ONNX Runtime ships a ~27MB native library per
  ABI. This is app-wide, so it affects every module, and an x86 emulator will not install the
  result.
- A model file in `assets/` is not a file on disk. ONNX Runtime opens a session from a path, which
  is why `PaddleModelAssets` extracts to `filesDir` first. Its digests are documented, so
  `ppocrv5_dict.txt` is pinned `-text` in `.gitattributes` — a Windows checkout converting it to
  CRLF breaks the digest and gives every dictionary entry a trailing carriage return.
- `google-services.json` must list **every** applicationId, and the `development` flavor's is
  `com.minion.scaffold.dev` because of its `applicationIdSuffix`. The plugin fails configuration
  with "No matching client found for package name" rather than falling back to the base id, so a
  Firebase console that only registers `com.minion.scaffold` breaks development builds while
  production ones pass.
- Text that can overflow uses `Modifier.basicMarquee()` with `maxLines = 1` and **no**
  `TextOverflow.Ellipsis`. Marquee measures its content with an infinite width constraint, so the
  ellipsis can never trigger and the two are not complementary — leaving it in is dead config.
