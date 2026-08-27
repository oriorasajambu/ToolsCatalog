# Android Scaffold

An opinionated, buildable starting point for Android apps: **Kotlin**, **Jetpack Compose**, **MVI**,
**Clean Architecture**, **Hilt**, and a Gradle multi-module graph whose boundaries are enforced by
the build rather than by code review.

The scaffold now carries a worked example — **ToolBox**, a small offline utility app: scan and build
EMV / Wi-Fi / link / vCard QR codes, text and generator tools, an on-device image-to-text reader, a
weather tool, and four sensor-driven instruments — a bubble level, a sound-level meter, a GPS
speedometer and an EXIF stripper. A home-screen App Widget holds up to five of those tools and
launches straight into them. All of it is assembled with the conventions below. Weather is the one deliberate
exception to the offline positioning — see [§1.1](#11-the-one-online-feature-weather) — everything
else works with no network at all, including the OCR, which runs its models locally
([§1.2](#12-on-device-machine-learning-ocr)). Clone it, rename the package, drop the `feature/*` you
do not want, and run `scripts/scaffold_feature.py` to generate the next vertical slice.

```bash
./gradlew build
```

> **Clone with [git-lfs](https://git-lfs.com) installed.** The OCR tool's PaddleOCR weights (~22MB
> under `feature/ocr/src/main/assets/`) are LFS objects. Without it you get pointer files and the
> app quietly falls back to its other recognition engine.

- **UI** — Jetpack Compose, Material 3; Midnight (dark) and Signal (light) themes that follow the system setting
- **Architecture** — MVI (State / Intent / Effect) over Clean Architecture layers
- **Async** — Coroutines + `StateFlow`; one-shot events over a buffered `Channel`
- **DI** — Hilt, with bindings living beside the implementations they bind
- **Network** — Retrofit + OkHttp + Gson; Chucker on debug builds only
- **Persistence** — Room, where a feature needs an offline cache (first used by `:feature:weather`); DataStore for per-feature preferences
- **On-device ML** — ML Kit (bundled models) and ONNX Runtime, behind one swappable interface
- **Build** — `development`/`production` flavors × `debug`/`release`; environment and signing read from gitignored properties files; R8 on release
- **Static analysis** — detekt, one config for every module, wired into `check` so a finding fails the build ([§2](#2-convention-plugins))
- **Widgets** — Glance, with a colour bridge so the home-screen widget and the app cannot drift apart
- **Testing** — JUnit + MockK + Turbine, with a shared `MainDispatcherRule`
- **Previews** — Showkase, aggregating every `@Preview` into a browsable catalog

---

## 1. Module graph

```
:app                    Application, MainActivity, NavHost, DI aggregation — nothing else
│
├── :core:common         Pure Kotlin. AppResult, DomainError, dispatcher qualifiers,
│                        UiState/UiIntent/UiEffect markers. Depends on nothing.
├── :core:domain         Pure Kotlin. Shared models, repository interfaces, use cases.
├── :core:navigation     Pure Kotlin. @Serializable route contracts only.
├── :core:designsystem   AppTheme, colour/type/shape tokens, dumb widgets.
├── :core:ui             MviViewModel, ObserveAsEvents, DomainError → @StringRes,
│                        PermissionState (the four-state gate shared by every permission).
├── :core:camera         The CameraX viewfinder — controller, torch, zoom, tap-to-focus,
│                        coordinate transform, optional still capture. Extracted from
│                        :feature:qrscan once :feature:ocr became a second consumer.
├── :core:network        Shared OkHttp/Retrofit, safeCall, error mapping.
├── :core:data           Data shared between features (not a feature's own data layer). Holds the
│                        widget's pinned-tool model and reconcile — two features need them and
│                        neither may depend on the other, which is the promotion rule in action.
├── :core:toolcatalog    The tool table every surface reads: ToolDescriptor, ToolCategory,
│                        ToolCatalog and the tool icons. Android rather than pure Kotlin, because
│                        an entry carries an ImageVector, a @DrawableRes and two @StringRes.
├── :core:testing        MainDispatcherRule, fakes. Consumed via testImplementation.
│
├── :core:emv            Pure Kotlin. EMV Merchant-Presented-Mode QR domain — parse and build.
├── :core:wifi           Pure Kotlin. Wi-Fi credential QR format (WIFI:T:…;S:…;P:…;;).
├── :core:url            Pure Kotlin. Web-link QR codes.
├── :core:vcard          Pure Kotlin. vCard 3.0 contact cards (RFC 2426).
├── :core:text           Pure Kotlin. Text / developer transforms — encoding, hashing, generators.
├── :core:weather        Pure Kotlin. WMO weather-code mapping, notable-condition thresholds,
│                        metric/imperial conversion — the one domain module a feature also owns a
│                        Retrofit API and a Room cache for (see §1.1).
├── :core:ocr            Pure Kotlin. Reading-order reconstruction, line→block grouping, the
│                        OcrEngine choice — the geometry, with none of the ML (see §1.2).
├── :core:level          Pure Kotlin. Tilt geometry, pose machine, gravity smoothing, flip
│                        calibration.
├── :core:sound          Pure Kotlin. A/C/Z weighting filters, time weighting, the Leq accumulator.
├── :core:gnss           Pure Kotlin. EGM96 geoid → height above sea level, speed and zero-speed
│                        rules, trip accumulators.
├── :core:exif           Pure Kotlin. JPEG/PNG/WebP container surgery — returns byte-range strip
│                        plans and never touches a file itself.
│
└── :feature:*           tools, qrscan, qrcreate, texttools, weather, ocr, level, soundmeter,
                         speedometer, exifstrip — one module per screen area — plus widget, which
                         draws no screen at all: it owns a manifest receiver, a provider XML and a
                         DataStore, and `:app` is supposed to hold nothing but wiring. (The domain
                         lives in the pure-Kotlin :core:* modules above; add more with
                         scripts/scaffold_feature.py.)
```

The four sensor modules are pure Kotlin for one reason: a level, a sound meter and a speedometer
have no visible ground truth on a phone, so the only way to know the maths is right is to prove it
against synthesised input in a JVM test. `import android.*` is a compile error there, not a review
comment.

### 1.1 The one online feature: weather

`:feature:weather` is the single exception to "this app works with no network" — it fetches from
[Open-Meteo](https://open-meteo.com) (free, no API key) because current conditions are inherently a
live-data problem, not something you can ship inside the binary the way an EMV table or a Wi-Fi QR
format can. It still degrades gracefully offline: every forecast it fetches is cached in its own
Room database (`forecast_cache`, keyed by location, stale after 3h) so the screen keeps showing the
last-known data — with a "stale" label — rather than an error, and only a location with **no** cache
at all shows a failure state. A second table, `saved_locations`, holds the cities the user added by
name; the GPS card is not in it, since it is pinned, undeletable and unorderable.

A few things about it worth knowing if you are using it as a template for another network-backed
feature:

- **Retrofit lives in the feature, not `:core:network`.** `:core:network` supplies the shared
  `OkHttpClient`/`Gson` singletons; `:feature:weather`'s own `di/WeatherNetworkModule.kt` builds
  two *more* `Retrofit` instances on top of them — Open-Meteo serves forecasts and place-name
  search from genuinely different hosts. Each host's URL is injected through its own qualifier
  (`@ForecastBaseUrl`, `@GeocodingBaseUrl`) rather than inlined, mirroring `:core:network`'s
  `@BaseUrl`. Those qualifiers are provided *by the feature*, not by `:app`: `@BaseUrl` comes from
  the app because it varies per flavor and is read from `BuildConfig`, whereas a fixed public API
  has no such split — routing it through `:app` would only teach the application module about
  hosts that are one feature's business. Note the helper that builds the two instances is private:
  exposing it as `@Provides Retrofit` would collide with `:core:network`'s unqualified binding.
- **Schema changes get a real migration.** `forecast_cache` is disposable, but `saved_locations` is
  user data, so `WeatherDatabase` ships a hand-written `MIGRATION_1_2` rather than
  `fallbackToDestructiveMigration()` — the destructive fallback would silently wipe someone's saved
  cities on a version bump, which is invisible until they complain.
- **Location comes from the platform `LocationManager`, not Play Services.** The repo carries no
  Play Services dependency, and `:feature:weather/data/location/LocationFixProvider.kt` uses
  `LocationManagerCompat.getCurrentLocation` so it keeps working on a device without Play Services.
- **The permission gate is modeled as state, not a separate screen or route.** `PermissionState` in
  `:core:ui` — `Unknown` / `Granted` / `Denied` / `PermanentlyDenied`, with the last one
  deep-linking to the app's system settings page rather than re-showing a dialog the system will
  not display again. It started as a copy in weather and another in qrscan; the third consumer
  (OCR) is what promoted it. Only the *state machine* is shared — each feature still writes its own
  rationale panel, because what to offer someone who declined genuinely differs: qrscan and OCR
  offer to pick an image instead, weather has no such fallback and gates hard.
- **Units are converted at the presentation edge, never on the way in.** Forecasts are fetched and
  cached in metric whatever the user picked; each ViewModel keeps the raw metric copy beside its
  state and re-derives the displayed numbers when the preference changes. Converting in place and
  writing back would lose the original after the first flip and turn the second one into
  °F-treated-as-°C. The DataStore preference itself lives in the feature, not `:core:data` — the
  repo promotes to a core module on the *second* consumer, not the first.

### 1.2 On-device machine learning: OCR

`:feature:ocr` photographs or picks an image and extracts the text, entirely on the device. It is
the counterpoint to weather: no network, no Play Services, nothing leaves the phone. The image is
never written to disk either — people OCR passports, receipts and bank letters — so only the
recognised *text* survives a process death, which is where the user's work actually is.

It ships **two engines behind one interface**, chosen at runtime in the tool's own settings screen:

| | |
|---|---|
| **ML Kit** (default) | Bundled Latin model. Fast enough to run on viewfinder frames. |
| **PaddleOCR PP-OCRv5** | Three ONNX models (~22MB) on ONNX Runtime. Slower, better on dense text. |

Worth knowing if you are adding a second implementation of anything:

- **The engine and the dispatcher are different interfaces.** `TextRecognitionEngine` is what ML Kit
  and PaddleOCR implement, and it does *not* report which engine it is. `TextRecognizer` — the seam
  the ViewModel injects — returns `Recognition(result, engine)`, naming the engine that actually
  ran. Only the thing that made the choice can say whether the choice was honoured, and PaddleOCR
  can fail to start (an unexpected ABI, a clone without git-lfs, a full disk, an OOM building the
  sessions). It falls back to ML Kit and the UI *says so*. A settings screen reading "PaddleOCR"
  while the other engine does the work is the hardest class of bug to diagnose: everything appears
  to function and only the results differ.
- **Reading order is reconstructed, not taken from the recognizer.** Both engines return regions in
  detection order, which scrambles receipts. `:core:ocr` groups them into rows by vertical overlap
  and orders rows top-to-bottom, blocks left-to-right — deliberately geometric rather than
  layout-aware, with the known two-column limitation documented in the KDoc.
- **Order first, merge second.** PaddleOCR detects *lines* where ML Kit reports *blocks*, so lines
  are merged into paragraphs to make the two comparable. Merging first and ordering afterwards
  looks equivalent and is not: a receipt's item list merges into one tall block that vertically
  overlaps every price beside it, collapsing them into one row where equal left edges leave the
  order arbitrary. The prices come out detached from their items and shuffled. `GroupLinesIntoBlocks`
  therefore orders the lines — uniform heights, so a printed row overlaps nothing else — and merges
  only consecutive ones. This was found by running a signed release against a real receipt, not by
  a test.
- **The models are assets, but ONNX Runtime wants a file path.** `PaddleModelAssets` extracts them
  to `filesDir` once, guarded by a version marker written *last* so an interrupted copy is redone
  rather than trusted. It does no checksum: the upstream project downloads over HTTP and must
  verify, whereas these ship inside a signed APK where the platform has already done it.
- **Sessions live for the screen, not the app.** ~22MB of weights plus the runtime's arena, released
  in `onCleared` rather than pinned for the process on a device that is also running a camera.
- **`:app` filters to `arm64-v8a` only.** ONNX Runtime ships a ~27MB native library per ABI.
  Dropping the x86 pair also shed ML Kit's, which nearly paid for the addition outright — the
  release APK grew 67.6MB → 71.9MB rather than the ~100MB of keeping every ABI.
- **The vendored pipeline is deliberately not refactored.** `data/paddle/vendor/` is
  [ente-io/mobile_ocr](https://github.com/ente-io/mobile_ocr) (MIT) with the package renamed and
  nothing else, so a re-sync is a diff rather than an archaeology exercise. It does not follow this
  repo's conventions and is not meant to; its README records the upstream commit.

### Dependency rules

```
:app ──► :feature:*  ──►  :core:*
:feature:a  ✗  :feature:b        never — route through :core:navigation
:core:*     ✗  :feature:*        never
:core:common depends on nothing
```

Three decisions do most of the work:

**`:core:common`, `:core:domain` and `:core:navigation` apply `minion.jvm.library`, not the
Android plugin.** "No Android imports in the domain layer" becomes a compile error instead of a
review comment.

**Everything in a feature module is `internal` except the navigation entry point.** A feature's
public API is `fun NavGraphBuilder.homeScreen(...)` and nothing else. Without `internal`, `:app`
can reach in and construct the ViewModel directly, and the module boundary is decorative.

**Bindings live with their implementations.** Each feature has its own `internal @Module`. A
central `:core:di` would have to depend on every feature, inverting the direction the graph exists
to enforce.

---

## 2. Convention plugins

`build-logic/` is an included build compiled before anything else configures. Module build files
apply one plugin and set a namespace; everything else is centralised, so the module count can keep
growing without any of them drifting apart.

| Plugin | Applies to | Provides |
|---|---|---|
| `minion.android.application` | `:app` | `com.android.application`, compileSdk/minSdk/Java level, desugaring, Compose, Hilt, Showkase, test wiring |
| `minion.android.library` | every Android library | the same, minus Compose; plus the `check` → `compileDebugAndroidTestKotlin` guard |
| `minion.android.library.compose` | modules that draw | Compose BOM + bundles, Showkase processor |
| `minion.android.hilt` | data-layer modules | Hilt + KSP, without dragging in Compose |
| `minion.android.feature` | every `:feature:*` | Compose + Hilt + navigation + the core modules a feature may see |
| `minion.jvm.library` | pure-Kotlin modules | `kotlin-jvm` only — deliberately no Android plugin |
| `minion.detekt` | applied by the three base conventions | detekt with one shared config, wired into `check` |
| `minion.githooks` | `:app`, via the application convention | `installGitHooks`, wired into `check` |

`check` depends on `compileDebugAndroidTestKotlin` on purpose. Neither `testDebugUnitTest` nor
`assembleDebug` builds `androidTest` sources, so they can stop compiling with a green board and
nobody finds out for weeks.

Dependency versions live in `gradle/libs.versions.toml`, shared by both builds.

### Static analysis

detekt rides along on the three base conventions rather than being requested per module, the same
way Showkase rides along on the compose one. One config file, `config/detekt/detekt.yml`, governs
everything: a rule that means something different in `:core:gnss` than in `:feature:qrscan` is a
rule that should not exist yet.

`buildUponDefaultConfig` is `false`. detekt ships around 150 rules active by default, and turning
all of them on against a codebase that has never run it produces a wall of unreviewed findings
rather than a usable first pass. Only the rules the config names are active, and each is there
because it mechanically enforces something this README already argues for in prose — the exhaustive
`when` of the MVI contract, the `CancellationException`-first rule in `safeCall`, the dispatcher
qualifiers in `:core:common`.

Two rules were tried and removed, which is the same bar working in the other direction. `UseDataClass`
produced six findings and no true positive — every one was a class that must *not* be a data class,
four of them holding an array where a generated `equals` compares references while reading like it
compares content. `MagicNumber` produced 230, of which the palette (where the hex *is* the colour),
spec-defined tables like the WMO codes, `:core:exif`'s byte masks and `@Preview` sample data are all
things that must not change. Removing a rule needs the same kind of evidence as adding one; both
removals record theirs in `detekt.yml`.

Where a rule is right almost everywhere, it is suppressed at the site with a comment saying why,
never weakened globally. A raised threshold would cover every case and record none of the reasons.

### Branch naming

`<type>/<kebab-subject>`, where the type is one the commit messages already use — `feat` `fix`
`build` `docs` `refactor` `perf` `chore`, plus `claude` for agent-generated names:

```
feat/quick-access-widget          build/detekt-baseline
fix/widget-pending-intent-identifier    docs/claude-md-widget
```

Enforced by `.githooks/pre-push`, which is tracked because git does not version `.git/hooks` — a
hook one machine has is a rule nobody else is held to. A tracked hook still does nothing until
`core.hooksPath` names its directory, so `:app:installGitHooks` sets it and `check` depends on that
task: the first `./gradlew build` in a fresh clone installs the hooks. `master`, tag pushes and
branch deletions all pass through, and `git push --no-verify` overrides it for a one-off.

Room and KSP are **not** part of any convention plugin — no module used them until
`:feature:weather`. A feature that needs a local database wires `androidx.room` + `ksp(room-compiler)`
directly in its own `build.gradle.kts` (`minion.android.hilt`, applied transitively via
`minion.android.feature`, already brings KSP), and keeps its own `RoomDatabase` rather than sharing
one — matching the rule that something only moves into a shared module once a second feature needs
it too.

---

## 3. Build variants & configuration

Two product flavors (the `environment` dimension) × two build types give four variants:

| | `debug` | `release` |
|---|---|---|
| **development** | `developmentDebug` | `developmentRelease` |
| **production** | `productionDebug` | `productionRelease` |

- **Flavor is which backend.** `development` carries a `.dev` `applicationId` suffix and a `-dev`
  version suffix, so it installs next to a production build; its launcher icon wears a **DEV** ribbon.
- **Build type is how the code is built.** `release` runs R8 and is signed with the release key;
  `debug` is debuggable and unminified.

### Environment (`BASE_URL`)

Not hard-coded — resolved per flavor from properties files at the repo root, first hit wins:

1. `local.properties` → `BASE_URL` — optional per-machine override (development only)
2. `dev.properties` / `prod.properties` — the gitignored per-checkout file
3. `dev.properties.template` / `prod.properties.template` — committed defaults, so a fresh clone still builds

`:app` is the only module that reads it; `:core:network` receives it through the `@BaseUrl`
qualifier and never touches `BuildConfig`.

### Release signing

`release` is signed with the `toolbox` keystore at the repo root. Neither the keystore nor its
passwords go in VCS: copy `keystore.properties.template` to `keystore.properties` (gitignored) and
fill in `storePassword` / `keyAlias` / `keyPassword`. Without them the release build is left unsigned
but still assembles. Confirm the wiring with `./gradlew :app:signingReport`.

### Minification

`release` runs R8 with resource shrinking (`isMinifyEnabled` and `isShrinkResources`).
`app/proguard-rules.pro` keeps only what reflection needs — kotlinx.serialization (type-safe
routes), Gson, ML Kit's manifest-named component registrars, and `ai.onnxruntime.**`, which the
native library resolves through JNI where R8 can see no reference to it. Everything else rides on
the libraries' own consumer rules.

Resource shrinking earns its keep as a review signal, not just bytes: it reports resources nothing
references, and twice that has meant a *string was written and the feature wired up wrong* rather
than a genuinely dead resource. Read the shrinker's report before deleting what it flags.

**Assemble and run a signed release on a device before shipping.** This repo has been burned three
times by failures invisible in debug: a Gson model stripped of its generic signature, ML Kit's
registrars renamed, and the OCR ordering bug in §1.2. A green `./gradlew build` is not evidence that
the release build works.

---

## 4. The MVI contract

Every feature declares exactly three types.

```kotlin
// State — what the UI renders. Flat data class, safe defaults for every field.
internal data class HomeState(
    val content: ContentState = ContentState.Loading,
    val query: String = "",
) : UiState {
    sealed interface ContentState {
        data object Loading : ContentState
        data class Success(val items: List<Article>) : ContentState
        data object Empty : ContentState
        data class Failure(val error: DomainError) : ContentState
    }
}

// Intent — everything the user can do. Sealed, so `when` is exhaustive.
internal sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data class Select(val id: String) : HomeIntent
}

// Effect — one-shot events that must not replay.
internal sealed interface HomeEffect : UiEffect {
    data class ShowError(val error: DomainError) : HomeEffect
    data class NavigateToDetail(val id: String) : HomeEffect
}
```

The mutually exclusive phases go in a nested `ContentState` rather than sibling booleans, because
`isLoading = true` next to a non-empty list is a state the UI would have to decide what to do
with — and this shape makes it unrepresentable.

**State or Effect?** Ask whether replaying it after a rotation would be wrong.

| | |
|---|---|
| Toast, snackbar, navigation | Effect |
| Loading flag, dialog visibility, list contents, text | State |

Storing a navigation flag in state means it fires again on every rotation. That bug is why
`Channel` exists here.

### The ViewModel

```kotlin
@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val getArticles: GetArticlesUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    override fun onIntent(intent: HomeIntent) {
        when (intent) {                       // no `else` — adding a case must not compile
            HomeIntent.Load -> load()
            is HomeIntent.Select -> viewModelScope.launch {
                emitEffect(HomeEffect.NavigateToDetail(intent.id))
            }
        }
    }

    private fun load() = viewModelScope.launch {
        getArticles()
            .onSuccess { items -> reduce { copy(content = ContentState.Success(items)) } }
            .onFailure { error ->
                reduce { copy(content = ContentState.Failure(error)) }
                emitEffect(HomeEffect.ShowError(error))
            }
    }
}
```

`MviViewModel` (in `:core:ui`) owns the `MutableStateFlow` and the `Channel`, exposes only
`state` and `effect`, and gives subclasses `reduce { }`, `currentState` and `emitEffect()`. No
screen can write state directly.

### Errors

Repositories and use cases return `AppResult<T>` — `Success(data)` or `Failure(DomainError)` —
never `kotlin.Result` and never a raw `Throwable`.

```
Exception ──► toDomainError() ──► DomainError ──► toMessageRes() ──► stringResource
   :core:network                    everywhere              :core:ui        the composable
```

- `safeCall { }` is the boundary. It rethrows `CancellationException` **before** catching
  `Throwable`, which is the line `runCatching` is missing — without it, cancelling a screen
  mid-request turns into a spurious failure and structured concurrency breaks silently.
- `DomainError` is sealed, so `when` needs no `else` and a new failure mode is a compile error at
  every site that must handle it.
- `e.message` never reaches state. It is developer text, frequently `null`, always untranslated.
- Only the composable turns an error into words, via `stringResource(error.toMessageRes())`.
- A feature's own errors do **not** extend `DomainError` — a sealed interface can only be extended
  from the same package *and* module, so that will not compile from `:feature:*`. Declare a sealed
  type in the feature, return it in the success channel (`AppResult<PlaceOrderOutcome>`) since a
  domain rejection is an answer rather than a failure, and map it to a `@StringRes` in the feature.

### Collecting effects

Use `ObserveAsEvents` from `:core:ui`, never `LaunchedEffect(Unit) { flow.collect { } }`. The
latter is scoped to composition, and a backgrounded screen stays composed — so a `navigate()`
fires while the app is invisible and the user comes back to the wrong screen. `ObserveAsEvents`
uses `repeatOnLifecycle(STARTED)`; because effects go through a buffered `Channel`, events sent
while stopped are held and delivered on resume.

---

## 5. Adding a feature

### With the script

```bash
python scripts/scaffold_feature.py --name Home
```

Writes the whole slice — contract, ViewModel, screen with four previews, navigation entry point,
domain model, repository interface, use case, DTO, Retrofit API, mapper, repository
implementation, two Hilt modules, strings and a passing ViewModel test — then prints the three
lines you still have to add. Useful flags: `--dry-run`, `--force`, `--no-remote`, `--no-tests`,
`--plural People`.

### By hand

```
feature/home/
├── build.gradle.kts                     # id("minion.android.feature") + namespace. That's all.
└── src/
    ├── main/kotlin/com/minion/scaffold/feature/home/
    │   ├── presentation/                # HomeContract, HomeViewModel, HomeScreen, HomeNavigation
    │   ├── domain/                      # Article, ArticleRepository, GetArticlesUseCase
    │   ├── data/                        # ArticleApi, ArticleDto, ArticleMapper, …RepositoryImpl
    │   └── di/                          # internal @Module — @Binds beside the impl it binds
    ├── main/res/values/strings.xml
    └── test/kotlin/…/presentation/      # HomeViewModelTest
```

Layers stay **packages**, not sub-modules. Split into `:feature:home:data` / `:domain` / `:ui`
only when separate teams own the layers — otherwise packages plus `internal` give identical
enforcement with none of the Gradle overhead.

Then three edits:

1. `settings.gradle.kts` — `include(":feature:home")`
2. `app/build.gradle.kts` — `implementation(project(":feature:home"))`
3. `app/.../navigation/AppNavHost.kt` — `homeScreen(onNavigateToDetail = { … })`

Routes go in `:core:navigation`, not the feature, so another feature can navigate here without
depending on this module. Pass navigation **down as lambdas**; never hand a feature the
`NavController`, or it can reach anywhere.

Build order that works: contract first (it is the spec), then domain, data, DI, ViewModel,
screen, navigation, tests.

---

## 6. Testing

```bash
./gradlew build              # compiles everything, runs unit tests, compiles androidTest, lints
./gradlew testDebugUnitTest  # unit tests only
```

`:core:testing` supplies `MainDispatcherRule` and re-exports JUnit, MockK, Turbine and
`kotlinx-coroutines-test` as `api`, so a feature's tests get them from one dependency — which the
feature convention adds automatically.

`MainDispatcherRule` defaults to `StandardTestDispatcher`, which queues coroutines instead of
running them eagerly, so tests control execution with `advanceUntilIdle()`. `UnconfinedTestDispatcher`
would hide ordering bugs by making everything look synchronous.

Assert typed `DomainError` values, never message substrings — a substring assertion passes for the
wrong reason the moment the copy is reworded.

---

## 7. Renaming for a new project

Everything is under `com.minion.scaffold`. To rebrand:

1. Replace `com.minion.scaffold` with your package across `*.kt`, `*.kts` and
   `scripts/scaffold_feature.py` (`BASE_PACKAGE`).
2. Move the source directories to match.
3. `settings.gradle.kts` — `rootProject.name`.
4. `app/build.gradle.kts` — `applicationId` (and the `.dev` suffix on the development flavor).
5. `app/src/main/res/values/strings.xml` — `app_name` (currently `ToolBox`); `themes.xml` if you
   rename `Theme.Scaffold`.
6. Replace the Midnight/Signal palettes in `:core:designsystem` with your own tokens, and swap the
   `ic_launcher_*` drawables (the development override lives in `app/src/development`).
7. Point `dev.properties` / `prod.properties` at your backends, and supply your own release keystore
   through `keystore.properties`.

---

## 8. Things that will bite you

- **`@ShowkaseRoot` must live in `src/main`, not `src/debug`.** KSP does not scan the debug source
  set for annotations, so a root placed there compiles and silently generates nothing. Only the
  processor is debug-scoped, so nothing reaches a release build either way.
- **A `@Preview` that Showkase should catalog must be `internal`, not `private`** — Showkase
  cannot call a private function. The compose convention sets `skipPrivatePreviews=true` so a
  throwaway private preview does not fail the build; it just will not appear in the catalog.
- **A type returned from a `@Provides` is part of that module's public surface.** Hilt generates
  the component in `:app`, so the type must be on `:app`'s compile classpath — use `api`, not
  `implementation`. This is why `:core:network` exposes the networking bundle as `api`, and why
  Chucker is built inside `provideOkHttpClient` rather than provided: providing it would put a
  debug-only library on every consumer's classpath.
- **Use `LocalResources.current`, not `LocalContext.current.getString()`.** A `Context` read is not
  invalidated by a configuration change, so text goes stale after a locale switch. Lint fails the
  build on this.
- **Give every `LazyColumn` item a stable `key`.** Without one, inserting at the top shifts every
  position and recomposes every row.
- **`LocationManagerCompat` needs `androidx.core` on the classpath explicitly.** Unlike the
  lifecycle/navigation/Hilt bundles, `minion.android.feature` does not pull in `androidx-core` for
  you — `:feature:weather/build.gradle.kts` adds `implementation(libs.androidx.core)` itself, the
  same way `:feature:qrscan` adds CameraX rather than putting it on every feature's classpath for
  one consumer.
- **A permission-gated feature's own `AndroidManifest.xml` declares the permission, not `:app`'s.**
  `:feature:weather` and `:feature:qrscan` each carry their own `<uses-permission>`, merged in by
  the manifest merger — this keeps a permission a feature actually needs out of `:app`'s manifest
  when that feature module is dropped.
- **Reverse geocoding can legitimately return nothing** — no Play Services, no geocoder backend on
  the device, or a fix over open water. `:feature:weather`'s `ReverseGeocoder` treats that as
  expected, not an error, and falls back to a formatted lat/lon string rather than blocking the
  pinned card on a service the app cannot guarantee.
- **Anything drawn over the camera preview needs fixed colours, not theme ones.** A theme colour
  answers to the palette rather than to the image behind it. The scan hint used `inverseOnSurface`,
  which is a *dark* colour in the dark theme, and was invisible against the reticle's own dark
  scrim — text that looked fine in a preview and vanished on a phone. `ScanReticle` and the OCR
  `BlockOverlay` both carry the rule where someone would reach for it.
- **`Modifier.basicMarquee()` and `TextOverflow.Ellipsis` are not complementary.** Marquee measures
  its content with an infinite width constraint, so the text never ellipsizes and the setting is
  dead config. Use `maxLines = 1` plus the marquee, and give the text a bounded width (a `weight`,
  or a parent that constrains it) or it sizes to its content and never registers as overflowing.
- **A clone without `git-lfs` looks like a working checkout.** The OCR models resolve to pointer
  files, PaddleOCR fails to start, and the app falls back to ML Kit with a notice rather than
  announcing that the repository is incomplete.
- **A `PendingIntent` is matched by `Intent.filterEquals`, which ignores extras.** Component,
  action, data, categories and identifier are compared; extras are not. The widget's five tiles
  differ only by a tool-id extra, so they are five *equal* intents, and the system hands every one
  of them the first `PendingIntent` it made — every tile opens whichever tool was drawn first, with
  nothing failing to say so. `Intent.setIdentifier` exists for this and has been available since
  API 29.
- **Glance is uniform in API surface and not in behaviour.** `cornerRadius` is backed by an API 31+
  `RemoteViews` call and is a silent no-op below it — square corners, no warning. Below API 31
  Glance also resolves colours into the `RemoteViews` when it renders, so a placed widget keeps its
  old palette through a light/dark switch while the app recomposes correctly. Treat any Glance
  modifier as version-gated until checked.
- **`abiFilters` in `:app` is app-wide.** It is set to `arm64-v8a` for ONNX Runtime's sake, which
  means an x86 emulator cannot install the app at all — including for features that have nothing to
  do with OCR.
- **Encoding a QR without naming a character set destroys non-ASCII text, in the code itself.**
  `MultiFormatWriter.encode` falls back to ISO-8859-1 when given no `EncodeHintType.CHARACTER_SET`,
  and zxing's encoder writes a literal `?` for every character that charset cannot hold. A merchant
  name in Arabic, Thai or Cyrillic is then destroyed at generation time: the code scans perfectly
  and reads back `????? ???????`, with a checksum that fails because tag 63 still carries the value
  computed from the text the code no longer contains. Every scanner in the chain is innocent, which
  is what makes this expensive to find — it presents as a decoding bug and is entirely an encoding
  one. Two things to recognise it by. **A question mark is an encoding artefact, never a decoding
  one:** reading bytes with the wrong charset yields `U+FFFD`, while `?` is what a `CharsetEncoder`
  substitutes for a character it cannot map, so its presence means something *wrote* the text badly
  rather than read it badly. And **a checksum that describes text the payload no longer holds means
  the damage happened after the checksum was computed** — which puts the encoder in the frame and
  rules the reader out. `encodeQrMatrix` declares UTF-8, which makes zxing emit an ECI segment
  naming the character set so the code describes its own encoding instead of leaving every reader
  to guess. The segment is added only for a payload that actually needs byte mode, so an ASCII
  payload encodes exactly as it did before. `QrCodeEncodingTest` reads a generated code back with
  **no** charset hint — asserting the encoder was called with the hint would prove nothing about
  what came out.
- Default Gradle dependencies to `implementation`; reach for `api` only when a type appears in the
  module's own public signatures.
