# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An opinionated Android scaffold: Kotlin, Jetpack Compose, MVI, Clean Architecture, Hilt, and a
Gradle multi-module graph whose boundaries are enforced by the build rather than by code review.
The repo currently carries a worked example app, **ToolBox** (`app_name` in strings.xml) — a
mostly-offline utility app whose tools cover scanning/comparing/exporting EMV codes, building EMV,
Wi-Fi, link and vCard QR codes, text and developer transforms, a random string generator, a weather
lookup, on-device OCR, a bubble level, a sound-level meter, a GPS speedometer and an EXIF stripper. A home-screen App
Widget holds up to five of those tools and launches straight into them.
Package root is `com.minion.scaffold`. 33 Gradle modules.

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
./gradlew build --no-configuration-cache  # REQUIRED after editing dev/prod/keystore.properties
python scripts/scaffold_feature.py --name Home   # generate a new feature slice (see below)
python scripts/generate_geoid.py                 # regenerate :core:gnss's EGM96 geoid (needs numpy + pillow)
./gradlew detekt                       # static analysis only, all modules (also runs as part of check/build)
```

There is no separate lint-only or ktlint task beyond what `./gradlew build` runs; Android Lint
runs as part of `build`. Static analysis beyond Lint is detekt (see *Static analysis* below), wired
into `check` the same way. `check` is also wired to compile `androidTest` sources on every module so
androidTest can't silently rot even though nothing normally builds it. The dependency is matched by
name pattern (`compile*DebugAndroidTestKotlin`) rather than named outright — a module with product
flavors gets one task per variant, so `:app` has `compileDevelopmentDebugAndroidTestKotlin` and
`compileProductionDebugAndroidTestKotlin` and never the unflavored name. Naming it directly made
`./gradlew build` fail outright while resolving `:app:check`. (No module currently has an
`androidTest` source set — the guard exists so that adding one can't rot unnoticed.)

First-time setup: copy `keystore.properties.template` → `keystore.properties` and
`dev.properties.template`/`prod.properties.template` → `dev.properties`/`prod.properties` if you
need real values; the repo builds without them (unsigned release, template `BASE_URL`).
`git clone` needs **git-lfs** — see *Things that will bite you*.

## Module graph

```
:app                    Application, MainActivity, NavHost, DI aggregation, feature flags — nothing else
├── :core:common         Pure Kotlin. AppResult, DomainError, dispatcher qualifiers, UiState/UiIntent/UiEffect markers.
├── :core:domain         Pure Kotlin. Shared models, repository interfaces, use cases. Currently FeatureFlagRepository.
├── :core:navigation     Pure Kotlin. @Serializable route contracts — the only channel between features.
├── :core:designsystem   AppTheme, colour/type/shape tokens, AppTextStyles, screen transitions, dumb widgets.
├── :core:ui             MviViewModel, ObserveAsEvents, DomainError → @StringRes, PermissionState, rememberClipboardCopy.
├── :core:camera         CameraX viewfinder — torch, zoom, tap-to-focus, still capture. Shared by qrscan and ocr.
├── :core:network        Shared OkHttp/Retrofit, safeCall, error mapping.
├── :core:data           Data shared BETWEEN features (not a feature's own data layer). The widget's
│                       pinned-tool model, its repository interface, and reconcilePinnedTools.
├── :core:testing        MainDispatcherRule, fakes — testImplementation only.
├── :core:emv/:wifi/:url/:vcard/:text   Pure-Kotlin domain logic for each QR/text format.
├── :core:weather        Pure Kotlin. WMO codes, unit conversion, notable-condition thresholds.
├── :core:ocr            Pure Kotlin. Reading-order reconstruction, line→block grouping, OcrEngine.
├── :core:level          Pure Kotlin. Tilt geometry, pose machine, gravity smoothing, flip calibration.
├── :core:sound          Pure Kotlin. A/C/Z weighting filters, time weighting, the Leq session accumulator.
├── :core:gnss           Pure Kotlin. EGM96 geoid → height above sea level, speed/zero-speed rules, trip accumulators.
├── :core:exif           Pure Kotlin. JPEG/PNG/WebP container surgery — returns byte-range strip plans, never touches a file.
├── :core:toolcatalog    The tool table: ToolDescriptor, ToolCategory, ToolCatalog, and the 14 tool
│                       icons. Android, not JVM — an entry carries an ImageVector and two @StringRes.
└── :feature:*           tools, qrscan, qrcreate, texttools, weather, ocr, level, soundmeter, exifstrip,
                         speedometer, widget — one per screen area, except `widget`, which draws none.
```

Dependency rules (enforced by convention plugins, not review):

```
:app ──► :feature:*  ──►  :core:*
:feature:a  ✗  :feature:b        never — route through :core:navigation
:core:*     ✗  :feature:*        never
:core:common depends on nothing
```

- `:core:common`, `:core:domain`, `:core:navigation` and the format/algorithm modules (`emv`,
  `wifi`, `url`, `vcard`, `text`, `weather`, `ocr`, `level`, `sound`, `gnss`, `exif`) apply
  `minion.jvm.library` (plain Kotlin, no Android plugin) — `import android.*` is a compile error
  there, not a review comment. That is deliberate for the sensor-driven modules in particular:
  a level, a sound meter and a speedometer have no visible ground truth on a phone, so the only
  way to know the maths is right is to prove it against synthesised inputs in a JVM test.
- Everything in a feature module is `internal` except its navigation entry point
  (`fun NavGraphBuilder.homeScreen(...)`). Without `internal`, `:app` could construct a
  ViewModel directly and the boundary becomes decorative.
- Hilt bindings live beside the implementations they bind, in each feature's own `internal
  @Module` — not centralized, since a central `:core:di` would have to depend on every feature.
- Promote something into `:core:data` or `:core:domain` only once **two** features need it; one
  feature needing it is that feature's own concern. `:core:data`'s README records the package
  layout to use when something does move there. The widget is the worked example: `PinnedTool`,
  `PinnedToolsRepository`, `WidgetPinRequester` and `reconcilePinnedTools` are there because
  `:feature:widget` renders the pinned list and `:feature:tools` owns the screen that edits it —
  two features, neither of which may depend on the other. The DataStore that implements the
  interface stayed in `:feature:widget`, because only one module writes it.
- **A feature that needs a fact only `:app` has declares the interface and lets `:app` bind it.**
  `WidgetLaunchIntentFactory` is in `:feature:widget` and bound in `:app`, because building the
  launch intent means naming `MainActivity`. The same shape as `FeatureFlagRepository` keeping
  Firebase out of every feature. The test of where a binding belongs is what it has to *name*:
  pin-to-home names the widget provider, which is `:feature:widget`'s own component, so that one is
  bound there and the receiver stays `internal`.

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

`minion.android.feature` grants a feature `:core:common`, `:core:domain`, `:core:navigation`,
`:core:designsystem`, `:core:ui`, `:core:network` and (as `testImplementation`) `:core:testing`.
`:core:data` is deliberately **not** granted — see the promotion rule above. A feature needing
anything else (`:core:camera`, a format module, DataStore, ML Kit) declares it in its own
`build.gradle.kts`.

compileSdk 37.1, minSdk 29, targetSdk 37, Java 11 with core-library desugaring, AGP 9 with its
built-in Kotlin support (applying `org.jetbrains.kotlin.android` alongside it is a hard error).
`minion.jvm.library` pins `jvmTarget = JVM_11` by hand — the standalone JVM plugin would otherwise
use the daemon's default and emit class files the Android modules cannot read.

Dependency versions live in `gradle/libs.versions.toml`, shared by the main build and `build-logic`.

## Static analysis

detekt is applied through the `minion.detekt` convention plugin, itself applied by the three base
module conventions (`minion.android.application`, `minion.android.library`, `minion.jvm.library`)
rather than requested per module — the same pattern `minion.android.library.compose` uses for
Showkase. One config file, `config/detekt/detekt.yml`, governs every module; a rule that means
something different in `:core:gnss` than in `:feature:qrscan` is a rule that should not exist yet.

detekt reads `src/main` only, and skips `**/vendor/**`. Both exclusions are in
`minion.detekt.gradle.kts` rather than the config file, because they are about which code is in
scope at all rather than which rules apply to it. A test's backtick name is its documentation, so
the doc guards fire on every `@Test` in the repo while saying nothing useful; and the PaddleOCR
sources under `:feature:ocr` are vendored, where restyling to house conventions is what turns the
next re-vendor into a merge conflict. Together they account for roughly four fifths of what detekt
reported before they were added.

`buildUponDefaultConfig` is `false`: detekt ships roughly 150 rules active out of the box, and
turning all of them on at once against a codebase that has never run it would produce a wall of
unreviewed findings rather than a usable first pass. Only the rules `detekt.yml` lists explicitly
are active, across eight rule sets — `complexity`, `potential-bugs`, `exceptions`, `coroutines`,
`empty-blocks`, `performance`, `comments`, `style` — and each one is there because it mechanically
enforces a convention this file already states in prose: `ElseCaseInsteadOfExhaustiveWhen` for the
MVI contract's exhaustive `when`s, `TooGenericExceptionCaught` and `SuspendFunSwallowedCancellation`
for the `safeCall` `CancellationException`-first rule, `InjectDispatcher` for `:core:common`'s
dispatcher qualifiers, `GlobalCoroutineUsage` against unstructured concurrency, `NotImplementedDeclaration`
against a scaffolded feature's `TODO()` surviving to merge, and `EmptyCatchBlock` against silently
swallowing what the `DomainError` pipeline exists to surface. The rest —
`UnsafeCallOnNullableType`/`UnsafeCast`, `SleepInsteadOfDelay`/`RedundantSuspendModifier`, the dead-code
group (`UnusedImports`/`UnusedParameter`/`UnusedPrivateProperty`/`UnusedPrivateMember`/`WildcardImport`),
`VarCouldBeVal`, the complexity ceiling (`LongMethod`/`LongParameterList`/
`CyclomaticComplexMethod`/`NestedBlockDepth`), the allocation-hygiene set (`SpreadOperator`/
`ForEachOnRange`/`CouldBeSequence`), and the public-API doc guard (`UndocumentedPublicClass`/
`UndocumentedPublicFunction`) — round out the same idea: general-purpose, low-noise rules rather
than project-specific ones. Add to the set the same way: a rule earns its place by encoding
something already true of the codebase or by being a cheap, high-confidence catch, not because it
is a detekt default.

**Two rules were tried and removed, which is the same bar working in the other direction.**
`UseDataClass` produced six findings and no true positive (see the `detekt.yml` comment).
`MagicNumber` produced 230, of which the palette in `Color.kt` (53, where the hex *is* the colour
definition), spec-defined codes like the WMO table (~35, where the `when` arm already names the
number), `:core:exif`'s byte masks and bit positions (47), and `@Preview` sample data (36) are all
things that must not change — while `:core:emv`, `:core:gnss` and `:core:sound`, the modules it was
turned on for, produced 18 between them. Removing a rule needs the same kind of evidence as adding
one; both removals record theirs in `detekt.yml`.

`check` depends on the `detekt` task the same way it depends on the androidTest-compile guard
above — a finding fails the build rather than sitting in a report nobody opens.

Several rules are scoped so they judge only what they can judge, each option earning its place the
same way a rule does. `LongMethod` skips `@Composable`: a composable describes a UI tree, so its
length tracks how much screen it covers rather than how much logic it holds. `LongParameterList`
keeps `@Composable` — a composable taking a dozen *required* arguments is the signal its state
wants to be one object — but stops counting defaulted parameters, which demand nothing of a caller,
and `@Inject` constructors, where the count measures how many collaborators a ViewModel needs.
`CyclomaticComplexMethod` gains `ignoreSingleWhenExpression`, because a method whose whole body is
one `when` is a dispatch table and the MVI contract requires exactly that shape in every
`onIntent`. `UndocumentedPublicClass` exempts companion objects, which are a keyword rather than
API surface. `UseDataClass` is off outright: every finding it produced was a class that must not be
one — four holding an array, where a generated `equals` compares references while reading like it
compares content, and two `@Inject` use cases where `copy()` is meaningless.

**A rule that is right almost everywhere is suppressed at the site, not weakened globally.**
Roughly twenty declarations carry a `@Suppress` with a comment saying why — a Retrofit method
whose `@Query` parameters bind one at a time, the biquad coefficients whose names its own KDoc
documents, the reusable form widgets the Compose API guidelines want explicit, the screen bodies
whose launcher-backed callbacks cannot be intents. Each reason is different, which is exactly what
a local suppression records and a raised threshold erases.

Pinned to the stable `io.gitlab.arturbosch.detekt` 1.23.8 line rather than the `dev.detekt` 2.x
line, even though 1.23.8 is built against older Kotlin metadata than this project's Kotlin 2.4.0
and there is a known issue reading Kotlin 2.3+ metadata (detekt/detekt#8865) — the 2.x line matches
the Kotlin version but is pre-1.0, and every other dependency in this repo is pinned to a stable
release. If `./gradlew detekt` fails on metadata rather than a real finding, that tradeoff is why.

**Rule names drift between detekt versions — verify against the actual pinned tag, not detekt's
own `main`-branch docs.** Found while writing this config: `main` (already tracking the 2.x line)
spells three of these rules differently than 1.23.8 does — `EmptyKotlinFile` there is `EmptyKtFile`
here, `DocumentationOverPrivateFunction` is `CommentOverPrivateFunction`, and
`RedundantVisibilityModifier` is `RedundantVisibilityModifierRule`. There is also no
`UnusedPrivateFunction` rule in 1.23.8 at all — `UnusedPrivateMember` is the one that catches an
unused private function (it needs type resolution the way `UnusedPrivateProperty` doesn't), and
detekt's own shipped defaults run both simultaneously rather than one instead of the other, which
`detekt.yml` follows. `config.validation: true` turns any of these mistakes into a hard failure
rather than a silently-ignored key, which is what caught them here.

## Build variants & environment

Two flavors (`development`/`production`, the `environment` dimension) × two build types
(`debug`/`release`). `development` gets a `.dev` applicationId suffix, a `-dev` version suffix,
and a DEV-badged launcher icon so it installs alongside production. `release` runs R8
(`app/proguard-rules.pro`), shrinks resources, uploads its mapping to Crashlytics, and is signed
with the `toolbox` keystore at the repo root when `keystore.properties` supplies the credentials —
otherwise the release build is left unsigned so a fresh clone or a CI job without secrets still
assembles.

`BASE_URL` resolves per flavor, first hit wins: `local.properties` (development-only override) →
`dev.properties`/`prod.properties` (gitignored) → `*.template` (committed fallback). Missing from
all three is a configuration error, not a silent fallback. Only `:app` reads it; `:core:network`
receives it via the `@BaseUrl` qualifier, never `BuildConfig`.

## Gradle performance settings (`gradle.properties`)

All four are on, each for a structural reason rather than by experiment — read the comments in the
file before changing one:

- **6 GB daemon heap + 1 GB metaspace, 3 GB for the Kotlin daemon.** The 2 GB default is a
  single-module figure; KSP runs in most modules and R8 processes the whole graph in one pass.
  The Kotlin compile daemon is a separate process and does not inherit `org.gradle.jvmargs`.
- **`org.gradle.parallel=true`.** Safe here because no project reads another's model during
  configuration — the convention plugins live in an included build and module files only declare
  dependencies.
- **`org.gradle.configuration-cache=true`.** The one setting whose payoff grows with module count.
  It also carries a live trap — see *Things that will bite you*.
- **`org.gradle.caching=true`.** What this actually buys is the debug↔release and
  development↔production switch, which otherwise recompiles the half of the graph that did not
  change.

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

A feature with a settings or calibration sub-screen (`qrscan`, `ocr`, `soundmeter`, `exifstrip`,
`speedometer`, `weather`, `level`) gives it its **own** contract + ViewModel + route rather than a
mode flag on the main one. Both are registered by the same feature's navigation file and both stay
`internal`.

## Error handling

Repositories and use cases return `AppResult<T>` (`Success`/`Failure(DomainError)`), never
`kotlin.Result`, never a raw `Throwable`.

```
Exception ──► toDomainError() ──► DomainError ──► toMessageRes() ──► stringResource
   :core:network                    everywhere              :core:ui        the composable
```

- `safeCall { }` (`:core:network`) is the exception boundary — it rethrows
  `CancellationException` before catching `Throwable`; a plain `runCatching` would turn a
  cancelled screen's request into a spurious failure. Anything else that catches broadly follows
  the same rule — `RemoteConfigFeatureFlagRepository` rethrows `CancellationException` first for
  exactly this reason.
- `DomainError` is sealed — a new failure mode becomes a compile error everywhere it must be
  handled. `e.message` never reaches state (developer text, often `null`, never localized); only
  the composable turns an error into words via `stringResource(error.toMessageRes())`.
- A feature's own errors do not extend `DomainError` (a sealed interface can only be extended
  from the same package+module). Declare a feature-local sealed type, return it in the success
  channel when it's a domain rejection rather than a failure, and map it to a `@StringRes` in the
  feature itself. `:feature:qrscan` has three of these — `QrScanError`, `CompareRejection`,
  `SchemaRefusal` — each kept separate because they mean different things to the person holding
  the phone ("this code is damaged" vs "wrong kind of code" vs "these two aren't comparable").

## Persistence

Feature preferences use **Preferences DataStore**, declared in the feature's own
`data/local/*PreferencesDataStore.kt` behind a repository interface in the feature's `domain/`.
It stays in the feature for the promotion rule above — only one feature reads it. The widget is the
one case where the *interface* was promoted to `:core:data` while the store stayed put: two
features read the pinned list, but only `:feature:widget` writes it.

**An ordered list is a delimited string, not a `stringSetPreferencesKey`.** A `Set` has no order,
and for the widget's pinned tools the order is half the point. `WidgetPreferencesDataStore` joins
ids with the ASCII unit separator, chosen because it cannot occur in a kebab-case tool id.

**Absent and present-but-empty must stay distinguishable** wherever a store seeds a default. The
widget seeds five tools on a first read by checking for a *missing key*: conflating that with an
empty list hands the defaults back to a user who deliberately unpinned everything, on every read,
with no way to make it stop.

Enums are stored **by name, never by ordinal**. An ordinal silently rebinds the moment an entry is
added or reordered, and km/h read back as knots is a factor of 1.85 with nothing on screen to
suggest anything changed. Read back with `Entries.firstOrNull { it.name == stored }` and a typed
default.

## Shared UI building blocks

Before writing one of these in a feature, check `:core:ui` and `:core:designsystem`:

- **`rememberClipboardCopy(snackbarHostState, label, confirmation)`** (`:core:ui`) — copying is the
  one action with no visible result, so the snackbar is the entire feedback for it. Six features
  copy something; `Clipboard.setClipEntry` suspends, which is why this is a remembered lambda.
- **`PermissionState`** (`:core:ui`) — `Unknown`/`Granted`/`Denied`/`PermanentlyDenied`, plus
  `resolve(granted, shouldShowRationale)`. Only call `resolve` with the answer to a *request*;
  `shouldShowRationale` is also false before the first one, so a cold check reports every
  never-asked permission as `PermanentlyDenied`. The rationale **UI** deliberately stays per
  feature — qrscan offers "paste a payload instead", weather has no such fallback.
- **`AppTextStyles`** (`:core:designsystem`) — `eyebrow` and `sectionHeading`, the tracked-out
  label treatments Material has no slot for. See the UI-token rules below for why they are not
  `.copy(letterSpacing = …)` at the call site.
- **`ScreenTransitions`** (`:core:designsystem`) — the push motion applied once as the NavHost
  default. A screen that needs modal (vertical) motion overrides it at its own `composable`.
- **`FormSection` / `FormField` / `PasswordField` / `PickerField`** (`:core:designsystem`) — the
  shared form widgets. `FormField` supports an IME action, a hint and a prefix.

## Feature flags

The home screen's tool catalog is filtered by Firebase Remote Config, through
`FeatureFlagRepository` (`:core:domain`) implemented by `RemoteConfigFeatureFlagRepository`
(`:app`). The implementation lives in `:app` because Remote Config is configured by
`google-services.json`, which is application identity; a feature reading a flag never learns
Firebase is behind it.

Two rules:

- **Key naming lives in exactly one place.** A tool id from `ToolCatalog` is kebab-case
  (`sound-meter`); a Remote Config key allows only letters, digits and underscores, so it becomes
  `feature_sound_meter_enabled`. `keyFor()` in the repository is the rule; `remote_config_defaults.xml`
  is the readable copy of what it produces.
- **Flags fail open.** Every default is `true`, an unknown id reads as enabled, and a fetch failure,
  a throttle or a non-boolean value in the console all leave the tool visible. Hiding a tool is
  always a deliberate act in the console, never something an outage can do.

Adding a tool means an entry in `ToolCatalog` — now in `:core:toolcatalog`, carrying an `AppRoute`
rather than an id so no `when` has to learn about it — **and** an entry in
`app/src/main/res/xml/remote_config_defaults.xml`. An entry also needs a `widgetIconRes`: the tools
screen draws the `ImageVector`, but Glance renders a `RemoteViews` tree and can only take a
drawable, so every tool carries the same glyph twice. The 14 drawables were generated from the path
data in `material-icons-extended`, the same source `Icons.Filled.*` is built from, and each records
that in its own comment — redraw one by hand and the two surfaces drift.

## Adding a feature

Prefer `python scripts/scaffold_feature.py --name Home` (flags: `--dry-run`, `--force`,
`--no-remote`, `--no-tests`, `--plural People`, `--output-dir`) — it writes contract, ViewModel,
screen with previews, navigation entry point, domain model, repository interface + impl, use case,
DTO, Retrofit API, mapper, two Hilt modules, strings, and a passing ViewModel test, then prints the
three manual wiring lines still needed:

1. `settings.gradle.kts` — `include(":feature:home")`
2. `app/build.gradle.kts` — `implementation(project(":feature:home"))`
3. `app/.../navigation/AppNavHost.kt` — `homeScreen(onNavigateToDetail = { … })`

If the feature is a user-facing tool, also add it to `ToolCatalog` (in `:core:toolcatalog`, with
both icon fields) and to `remote_config_defaults.xml` (see *Feature flags*). Nothing else needs
touching for it to be pinnable to the home-screen widget — the widget reads the same catalog.

Layers are packages (`presentation/`, `domain/`, `data/`, `di/`) inside one feature module, not
separate Gradle modules — split into submodules only when different teams own the layers. Build
order that avoids rework: contract → domain → data → DI → ViewModel → screen → navigation → tests.
Routes belong in `:core:navigation`, never the feature itself, so other features can navigate to
it without depending on the module. Route arguments must be ids and primitives, never domain
models. Pass navigation down as lambdas — never hand a feature the `NavController`.

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
- **A `@StringRes` in a long-lived list, not a resolved `String`.** `ToolCatalog` is built once at
  class-init time; a `String` resolved then would not follow a locale change.

A literal that survives review because it "isn't user-facing" or "is only a number": `testTag`
identifiers, log tags, and `@Preview` sample data are genuinely exempt, and so is `0.dp`. Nothing
else is.

## Testing

`:core:testing` re-exports JUnit, MockK, Turbine and `kotlinx-coroutines-test` as `api`, added
automatically by the feature convention plugin. `MainDispatcherRule` defaults to
`StandardTestDispatcher` (coroutines queue, tests drive them with `advanceUntilIdle()`) —
deliberately not `UnconfinedTestDispatcher`, which would hide ordering bugs by making everything
look synchronous. Assert typed `DomainError` values, never message substrings.

Where the tests actually are: the pure-Kotlin algorithm modules carry the bulk of them
(`:core:emv`, `:core:level`, `:core:gnss`, `:core:sound`, `:core:exif`, `:core:ocr`, the format
modules), because that is the code with a checkable ground truth. Feature modules test their
ViewModels and their feature-local use cases. Fakes for a feature's own repository live in that
feature's `src/test` (e.g. `FakePaymentSchemaRepository`), not in `:core:testing`.

## Renaming this scaffold for a new project

Everything is under `com.minion.scaffold`; see [README.md § 7](README.md) for the full checklist
(package rename, `settings.gradle.kts` root name, `applicationId`, `app_name`/theme name,
designsystem palette + launcher icons, `dev.properties`/`prod.properties`, keystore,
`google-services.json`).

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
`-keepattributes SourceFile, LineNumberTable` in `app/proguard-rules.pro` must stay; the debug type
turns the upload off, since a non-minified build produces no mapping file and the task would look
for one that is never written.

## Things that will bite you

- **The configuration cache does not see `keystore.properties`, `dev.properties` or
  `prod.properties`.** `app/build.gradle.kts` reads all three through `java.util.Properties` at
  configuration time, which the cache cannot track as an input. So editing `dev.properties` and
  rebuilding gives you the *previously cached* `BASE_URL`, with no warning and no failure — the app
  just talks to the old backend. After changing any of those three files, either run once with
  `--no-configuration-cache`, or invalidate the cache. The real fix is reading them through
  `providers.fileContents(...).asText`, which the cache does track; until that lands, this is a
  live trap.
- **An enum used as a route argument needs `@Keep`.** R8 renames enum entry fields, and the
  generated serializer resolves entries by name — so under minification, decoding the argument
  throws and navigating to the screen fails. `ScanPurpose` carries the annotation; another enum
  added to `Routes.kt` needs its own. The route *classes* do not: their serial names are read only
  by `composable<T>`/`navigate(T)`, both compiled in the same R8 run.
- **A ViewModel reads a route argument by name, not via `toRoute()`.** The typed decoder builds an
  `android.os.Bundle`, which does not exist in a JVM unit test — using it would make every
  ViewModel test need Robolectric. Read `savedStateHandle[QrScanRoute.ARG_PURPOSE]` instead, and
  note the handle holds the **decoded enum**, not its name: typing it as `String` compiles, passes
  a test that seeds a `String`, and throws `ClassCastException` on the first real navigation.
- **Store enums in DataStore by name, never by ordinal** — see *Persistence*.
- `@ShowkaseRoot` must live in `src/main`, not `src/debug` — KSP doesn't scan the debug source
  set, so it silently generates nothing there.
- A `@Preview` Showkase should catalog must be `internal`, not `private` (Showkase can't call a
  private function); the compose convention sets `skipPrivatePreviews=true` so a stray private
  preview just doesn't show up rather than failing the build. The browser has no launcher icon —
  the home screen's brand tile opens it, and only in a debug build.
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
- A `data class` whose property is typed as an interface (like `Tool.route: AppRoute`) reads as
  unstable to Compose and recomposes with everything above it. Annotate `@Immutable` where nothing
  is ever mutated.
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
- `:core:gnss` ships its EGM96 geoid as a **JVM resource** (`src/main/resources/egm96_geoid.bin`),
  not an Android asset — that is what lets the altitude conversion be checked at a hundred thousand
  points in a plain JVM test. Regenerate it with `scripts/generate_geoid.py`, never by hand.
- `google-services.json` must list **every** applicationId, and the `development` flavor's is
  `com.minion.scaffold.dev` because of its `applicationIdSuffix`. The plugin fails configuration
  with "No matching client found for package name" rather than falling back to the base id, so a
  Firebase console that only registers `com.minion.scaffold` breaks development builds while
  production ones pass.
- **The widget's DataStore name appears in three files.** `preferencesDataStore(name =
  "widget_preferences")` in `WidgetPreferencesDataStore`, and the `<include>` path in both
  `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`. Renaming the store
  without changing both XML files stops the backup silently — no build failure, no runtime error,
  just a user who restores a device and finds an empty widget. The two XML files must also agree
  with *each other*: the first is read on API 30 and below, the second on 31+, so a file listed in
  one and forgotten in the other is backed up on half your devices.
- **A `PendingIntent` is matched by `Intent.filterEquals`, which ignores extras.** Action, data,
  type, component, categories and identifier are compared; extras are not. Five widget tiles that
  differ only by a tool-id extra are therefore five *equal* intents, and the system hands every one
  of them the first `PendingIntent` it made — every tile opens whichever tool was drawn first, with
  nothing failing anywhere to say so. `Intent.setIdentifier` exists for exactly this and has been
  available since API 29, which is this app's minSdk. `WidgetLaunchModule` sets it to the tool id.
- **Glance's `cornerRadius` does nothing below API 31.** It is backed by
  `RemoteViews.setViewOutlinePreferredRadius`, added in API 31, and on 29–30 it is a silent no-op:
  the widget draws square corners and no warning is emitted. `QuickAccessWidget` takes the platform
  radius above 31 and a tinted shape drawable below. Treat any Glance modifier as version-gated
  until checked — the API surface is uniform, the behaviour is not.
- **Glance resolves colours into the `RemoteViews` at render time below API 31.** A widget already
  on the home screen keeps its old palette through a light/dark switch while the app recomposes
  correctly, so "the widget did not follow the theme" is a *redraw* problem, not a colour-bridge
  one. `ACTION_CONFIGURATION_CHANGED` cannot be a manifest receiver, which is why `AppApplication`
  hears it and asks `WidgetSynchroniser` for a redraw.
- Text that can overflow uses `Modifier.basicMarquee()` with `maxLines = 1` and **no**
  `TextOverflow.Ellipsis`. Marquee measures its content with an infinite width constraint, so the
  ellipsis can never trigger and the two are not complementary — leaving it in is dead config.
