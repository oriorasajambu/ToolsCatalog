# Quick Access Widget — Specification

A home-screen App Widget holding up to five tools from the ToolBox catalog, configured from
inside the app. Tapping a tile launches the app straight into that tool.

This document is the design of record. It states the decisions and — following the house style
of [CLAUDE.md](CLAUDE.md) — the reason each was made, so a later change can tell a constraint
from an accident.

---

## 1. Scope

**In scope**

- One widget type: a single horizontal strip of up to five tool tiles.
- A global (not per-instance) pinned list of ≤ 5 tool ids, persisted on device and backed up.
- An in-app configuration screen: pick, order and unpin tools; optionally request that the
  launcher pin the widget.
- A launch path from a widget tile into the corresponding tool screen.
- Reconciliation of the pinned list against the feature-flag configuration and against the
  catalog that actually shipped.

**Out of scope**

- Per-widget-instance configuration, and the `android:configure` activity that would imply.
- Multiple widget shapes or sizes beyond one resizable strip.
- Live data on the widget (no weather reading, no last-scanned code). Tiles are launchers, nothing
  more.
- Launcher shortcuts (`ShortcutManager`) — a separate surface, not part of this work.

---

## 2. Decisions at a glance

| # | Question | Decision |
|---|---|---|
| 1 | Where does the catalog live? | New `:core:toolcatalog` Android library |
| 2 | Widget rendering | Glance, with an explicit theme bridge |
| 3 | Launch mechanism | Explicit intent to `MainActivity` + tool-id extra |
| 4 | Configuration scope | Global — one shared list, all instances identical |
| 5 | Flag-disabled pinned tool | Kept pinned, rendered greyed, tap opens tools home |
| 6 | Tool removed from binary | Pruned silently on reconcile |
| 7 | Layout | One row, ≤ 5 cells, responsive icon-only → icon + label |
| 8 | Back stack | Reset to tools home, then push the tool |
| 9 | First-run default | First five *enabled* catalog tools, in catalog order |
| 10 | Icons | Catalog carries both `ImageVector` and `@DrawableRes` |
| 11 | State ownership | App-side DataStore, single writer, read via Hilt `EntryPoint` |
| 12 | Config screen | Own route + MVI contract in `:feature:tools`; cap blocks at 5 |
| 13 | Redraw triggers | Config save, flag snapshot, locale change, upgrade/boot |
| 14 | Colour | Material You on API 31+, bridged app palette below |
| 15 | Reorder | Hand-rolled drag-and-drop |
| 16 | Cap semantics | Five *pinned ids*, greyed or not |
| 17 | Size | Horizontal resize only; 5×1 target, 4×1 min |
| 18 | Backup | Included in backup + device transfer, reconciled on read |
| 19 | Analytics | One `widget_tool_opened` event carrying the tool id |
| 20 | Picker preview | `previewLayout` on API 31+, static PNG on 29–30 |
| 21 | Permissions | No special handling — the target screen gates itself |
| 22 | Entry point | Icon button in `HomeHeader` |
| 23 | Empty list | Legal; widget draws a "Choose tools" prompt |

---

## 3. Module structure

Two new Gradle modules. `settings.gradle.kts` gains two `include` lines; `:app` gains one
`implementation`.

```
:app ──────────────► :feature:widget ──► :core:toolcatalog ──► :core:navigation
  │                                            ▲
  └──────────────► :feature:tools ─────────────┘
```

### 3.1 `:core:toolcatalog` (new)

Applies `minion.android.library.compose`. Namespace `com.minion.scaffold.core.toolcatalog`.

**Why an Android library and not `:core:domain`.** The catalog carries an `ImageVector`, a
`@DrawableRes` and two `@StringRes`. `:core:domain` applies `minion.jvm.library`, where
`androidx.annotation` and Compose UI are not on the classpath — putting the catalog there is a
compile error, not a style preference. A pure-Kotlin id/route half with an Android presentation
half on top was considered and rejected: it needs a sealed `ToolId` plus an exhaustive `when` to
stay complete, which is more machinery than a 14-entry table earns.

**Why not `:core:designsystem`.** That module owns tokens and dumb widgets. Making it depend on
`:core:navigation` and know which features exist is the drift the module rules exist to prevent.

Contents, moved from `:feature:tools`:

- `ToolDescriptor` (the current `Tool`, made `public`), `ToolCategory`, `ToolCatalog`.
- `res/drawable/ic_tool_*.xml` — 14 vector drawables, one per tool.
- `res/values/strings.xml` — the 14 `tools_*_title` and 14 `tools_*_description` strings.

Depends on `:core:navigation` only.

**What stays in `:feature:tools`:** its layout strings (section headings, hero copy, the brand
tile), its dimens, `ToolsState`/`ToolsIntent`/`ToolsEffect`, `ToolsViewModel`, `ToolsScreen`.
`ToolsState.tools` becomes `List<ToolDescriptor>`; the grouping properties are unchanged.

### 3.2 `:feature:widget` (new)

Applies `minion.android.feature`. Namespace `com.minion.scaffold.feature.widget`.

**Why a module and not `:app`.** The widget carries a manifest component, an appwidget-provider
XML, a preview layout, a DataStore, a repository implementation and its own tests. Putting all of
that in `:app` contradicts CLAUDE.md's "`:app` — nothing else". It is not a screen, so "feature" is
a mild stretch; the alternative is a `:core:` module owning a manifest component, which is worse.

Contents:

```
feature/widget/
├── src/main/AndroidManifest.xml        receiver declarations (merged into :app)
├── src/main/res/xml/quick_access_widget_info.xml
├── src/main/res/layout/widget_preview.xml
├── src/main/res/drawable-nodpi/widget_preview.png
├── src/main/res/values/{strings,dimens}.xml
└── src/main/kotlin/…/widget/
    ├── data/local/WidgetPreferencesDataStore.kt
    ├── glance/
    │   ├── QuickAccessWidget.kt            GlanceAppWidget
    │   ├── QuickAccessWidgetReceiver.kt    GlanceAppWidgetReceiver
    │   ├── WidgetGlanceTheme.kt            the colour bridge
    │   └── WidgetUpdateReceiver.kt         locale / package-replaced / boot
    ├── di/WidgetModule.kt                  binds the repository
    ├── WidgetEntryPoint.kt                 EntryPointAccessors hook for Glance
    └── WidgetLaunchIntentFactory.kt        interface, bound in :app
```

Extra dependencies in its `build.gradle.kts`: `:core:toolcatalog`, `:core:data`,
`libs.data.store`, the Glance bundle.

### 3.3 `:core:data` (existing, currently a README only)

Gains its promised second consumer. Holds the pieces both `:feature:widget` and `:feature:tools`
need:

```
core/data/…/widget/
├── PinnedTool.kt                 id + availability
├── PinnedToolsRepository.kt      interface
└── ReconcilePinnedTools.kt       pure function — the only real logic
```

Rationale in §9.1. `:core:data` is **not** granted by `minion.android.feature`, so both consuming
modules declare it explicitly.

### 3.4 Version catalog

```toml
[versions]
glance = "1.2.0"          # confirm the current stable at implementation time

[libraries]
glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
glance-material3  = { group = "androidx.glance", name = "glance-material3",  version.ref = "glance" }

[bundles]
glance = ["glance-appwidget", "glance-material3"]
```

`glance-material3` is what supplies `GlanceTheme` and the dynamic-colour providers.

---

## 4. Data model

### 4.1 Persisted state

One Preferences DataStore, `widget_preferences`, owned by `:feature:widget`. It holds exactly one
key:

```kotlin
private val PINNED_TOOL_IDS = stringPreferencesKey("pinned_tool_ids")
```

Value: the pinned tool ids joined by the ASCII unit separator (``), in display order. Order
is the data — the widget renders left to right in exactly this order. The separator is chosen
because it cannot occur in a kebab-case tool id.

**Why a delimited string and not `stringSetPreferencesKey`.** A `Set` has no order, and order is
half the point of the configuration screen.

**Why ids and not serialized routes.** A tool id is a stable string the Firebase console already
keys against (`feature_<id>_enabled`). A serialized `AppRoute` in persistent storage would be a
second thing R8 could rename out from under a stored value — the same class of trap that
`ScanPurpose`'s `@Keep` exists to close. Ids are also what makes `qr-scan` and `qr-edit`
distinguishable when both resolve to `QrScanRoute`.

**Cap.** Five ids. Enforced on write in the repository, and again on read (`.take(5)`) so a value
written by a future build with a larger cap truncates visibly instead of overflowing the strip.

### 4.2 Domain types

```kotlin
/** A pinned tool as the widget should draw it. */
data class PinnedTool(
    val descriptor: ToolDescriptor,
    val isAvailable: Boolean,
)
```

`isAvailable = false` means "the catalog still has this tool, but the remote configuration is
currently withholding it". A tool absent from the catalog produces no `PinnedTool` at all — see
§4.3.

### 4.3 Reconciliation

The one piece of genuine logic, extracted as a pure function so it can be proved in a JVM test:

```kotlin
fun reconcilePinnedTools(
    storedIds: List<String>,
    catalog: List<ToolDescriptor>,
    flags: FeatureFlags,
): ReconcileResult
```

returning both the tools to render and the ids to persist:

```kotlin
data class ReconcileResult(
    val tools: List<PinnedTool>,   // render order, ≤ 5
    val retainedIds: List<String>, // what should be written back, ≤ 5
)
```

Rules, in order:

1. **Unknown id → dropped.** An id with no catalog entry is a tool that no longer ships. It is
   removed from `retainedIds` and never rendered. There is no title to draw and no way it can come
   back, so keeping it would mean a permanent placeholder string — user-visible debt for a
   situation that resolves itself.
2. **Known but flag-disabled → retained, unavailable.** The id stays in `retainedIds`, and its
   `PinnedTool` carries `isAvailable = false`. The flag is reversible; the user's arrangement
   should survive a console mistake or a temporary kill switch.
3. **Duplicates → first occurrence wins.** Defensive; the config screen cannot produce one.
4. **Truncate to 5** after the above.

`retainedIds != storedIds` is the signal to write back. The write happens on the app side only
(§6.3) — the widget never writes.

**Cap semantics.** The cap counts pinned ids, available or not. An unavailable tool still holds
its slot; the config screen shows it selected with a caption, and unpinning it is how the user
frees the slot. One rule, no hidden state.

---

## 5. The widget

### 5.1 Provider declaration

`res/xml/quick_access_widget_info.xml`:

```xml
<appwidget-provider
    android:minWidth="250dp"
    android:minHeight="50dp"
    android:targetCellWidth="5"
    android:targetCellHeight="1"
    android:minResizeWidth="180dp"
    android:resizeMode="horizontal"
    android:widgetCategory="home_screen"
    android:description="@string/widget_description"
    android:previewImage="@drawable/widget_preview"
    android:previewLayout="@layout/widget_preview"
    android:updatePeriodMillis="0" />
```

- `targetCellWidth`/`targetCellHeight` are API 31+; `minWidth`/`minHeight` are the 29–30 fallback
  and must stay in agreement with them (5 cells ≈ 250dp, 1 cell ≈ 50dp on the standard grid).
- `minResizeWidth` at 180dp lets a user shrink to roughly four cells. Below that the tiles stop
  clearing the 48dp minimum touch target.
- `resizeMode="horizontal"` only. Extra height buys a strip of launchers nothing, and extra
  breakpoints to design and verify is a real cost.
- `updatePeriodMillis="0"`. Nothing on the widget is time-varying, so there is no periodic update.
  Every redraw is triggered explicitly (§7).

### 5.2 Layout

One `Row`, evenly distributed, holding one tile per pinned tool. Fewer than five pinned means
wider tiles, never blank slots.

`SizeMode.Responsive` with two sizes:

| Breakpoint | Height | Renders |
|---|---|---|
| Compact | < 70dp | Icon only |
| Regular | ≥ 70dp | Icon above a single-line label |

The label uses the tool's title. It is single-line and truncated; a widget tile is not the place
for `basicMarquee`, and Glance has no equivalent anyway.

**Accessibility.** Every tile carries a `contentDescription` built from the tool title, in *both*
breakpoints. Icon-only is a visual decision, never a semantic one — a TalkBack user must hear the
same five tool names regardless of the widget's height. An unavailable tile's description appends
the "currently unavailable" string so the state is audible, not only visible.

### 5.3 Tile states

| State | Appearance | Tap |
|---|---|---|
| Available | Icon at full opacity, label `onSurface` | Opens the tool |
| Unavailable | Icon and label at reduced alpha, drawn in `onSurfaceVariant` | Opens the tools home |
| — (empty list) | Single full-width "Choose tools" tile | Opens the config screen |

The alpha value is a named constant in `:feature:widget`, not a literal at the call site.

### 5.4 Colour

```kotlin
@Composable
internal fun WidgetGlanceTheme(content: @Composable () -> Unit) {
    val colors =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors
        else WidgetColorProviders.app
    GlanceTheme(colors = colors, content = content)
}
```

- **API 31+** uses Glance's dynamic providers, so the widget themes itself to the wallpaper like
  every system widget beside it.
- **API 29–30** uses `WidgetColorProviders.app`, a `ColorProviders` built from the app's own two
  schemes — `ColorProvider(day = Signal…, night = Midnight…)` per role, so light/dark still follows
  the system exactly as `AppTheme` does. This lives in `:core:designsystem` beside `AppTheme`, not
  in the widget module: two independently maintained copies of the palette is how a widget drifts
  from its app.

Background is `GlanceModifier.background(GlanceTheme.colors.widgetBackground)` with
`cornerRadius(android.R.dimen.system_app_widget_background_radius)` on API 31+, and a fixed radius
dimen below. A transparent background was rejected for the reason CLAUDE.md already records about
camera overlays: a theme colour answers to the palette, not to the wallpaper behind it.

### 5.5 Picker preview

- **API 31+**: `previewLayout` — a hand-written RemoteViews XML showing five representative tiles.
- **API 29–30**: `previewImage` — a PNG in `res/drawable-nodpi`.

Both are static and will drift from the Glance layout as it changes. That is accepted; the
mitigation is a note in the preview layout's own comment pointing at `QuickAccessWidget.kt`.
Neither preview reflects the user's actual pinned tools — the picker has no access to them.

---

## 6. State plumbing

### 6.1 Repository

```kotlin
interface PinnedToolsRepository {
    /** The pinned ids as stored, in order. */
    val pinnedIds: Flow<List<String>>

    /** Replaces the list wholesale. Capped and de-duplicated on write. */
    suspend fun setPinned(ids: List<String>)

    /** One-shot read, for the widget's render pass. */
    suspend fun currentPinnedIds(): List<String>
}
```

Interface in `:core:data`, implemented by `WidgetPreferencesDataStore` in `:feature:widget`,
following the same shape as `SoundMeterPreferencesDataStore`.

### 6.2 Reading it from Glance

`GlanceAppWidgetReceiver` is instantiated by the framework and cannot be `@AndroidEntryPoint`.
The widget reaches the graph through an entry point:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun pinnedToolsRepository(): PinnedToolsRepository
    fun featureFlagRepository(): FeatureFlagRepository
}
```

`provideGlance` resolves it with `EntryPointAccessors.fromApplication(context)`, reads
`currentPinnedIds()` and the first `flags()` emission, reconciles, and renders.

**Why the first flag emission is safe here.** `FeatureFlagRepository.flags()` emits immediately
with the activated-or-default configuration and never fails (see its KDoc). The widget therefore
has a value to render on its first frame and never needs a loading state.

**First-frame handling.** DataStore's first read suspends. `provideGlance` is a suspend function,
so this is a plain await and not a race — but the seeded-default path (§6.4) must have already run,
or a fresh install renders the empty prompt once before the seed lands.

### 6.3 Single writer

Only the app process writes: the config screen's ViewModel, and the reconcile-writeback in `:app`.
The widget reads and never writes. There is no second store and therefore nothing to keep in sync.

Glance's own `GlanceStateDefinition` is deliberately unused. Mirroring a global configuration into
per-instance widget state reintroduces a sync problem the global model does not have.

### 6.4 Seeding the default

On first read — `pinnedIds` **absent** from the store, which must stay distinguishable from
present-and-empty — the repository returns the first five *enabled* catalog tools in catalog order,
and writes them. If absent and empty are conflated, a user who deliberately unpins everything gets
the defaults back on the next launch.

In catalog order that seed is: `qr-scan`, `qr-edit`, `qr-create`, `wifi-create`, `url-create`.

> **Noted, not blocking:** that is a create-heavy strip for a quick-access surface, and the more
> obviously glanceable tools (level, sound meter, speedometer) never make the cut. The spec keeps
> catalog order as decided; if the seed proves poor in use, the fix is a `DEFAULT_PINNED_IDS`
> constant in `ToolCatalog` — a one-line change this design does not foreclose.

### 6.5 Backup

Both `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` must include the
DataStore file:

```xml
<!-- backup_rules.xml (API ≤ 30) -->
<full-backup-content>
    <include domain="file" path="datastore/widget_preferences.preferences_pb" />
</full-backup-content>
```

```xml
<!-- data_extraction_rules.xml (API 31+) -->
<data-extraction-rules>
    <cloud-backup>
        <include domain="file" path="datastore/widget_preferences.preferences_pb" />
    </cloud-backup>
    <device-transfer>
        <include domain="file" path="datastore/widget_preferences.preferences_pb" />
    </device-transfer>
</data-extraction-rules>
```

Both files are currently all-comment templates, so adding an `<include>` narrows backup from
"everything" to "this file only" for the `full-backup-content` path. Verify that nothing else in
the app relies on the current implicit full backup before landing this.

Android does not restore widget *placement*, so a restored user re-drops the widget and finds their
five already set. The reconcile pass on first read handles ids a newer build no longer has.

> ⚠️ **Bite:** the DataStore name appears in three places — the `preferencesDataStore(name = …)`
> call and both XML files. Renaming the store without updating the XML silently stops the backup,
> with no build failure and no runtime error. Worth an entry in CLAUDE.md's *Things that will bite
> you*.

---

## 7. Redraw triggers

Nothing about the widget is time-varying, so every update is explicit. `updateAll()` is called:

| Trigger | Where | Why |
|---|---|---|
| Config saved | Config ViewModel, after the write | The obvious one |
| New `FeatureFlags` snapshot | `:app`, on the flag stream | Grey/ungrey a tile as the console flips |
| `ACTION_LOCALE_CHANGED` | `WidgetUpdateReceiver` | Labels are `@StringRes` resolved at render; a widget is **not** re-rendered on a locale switch, so labels stay in the old language indefinitely. Same class of bug as the `LocalResources` rule in CLAUDE.md |
| `ACTION_MY_PACKAGE_REPLACED` | `WidgetUpdateReceiver` | An update can remove a tool; the reconcile prune must run, or a tile with no catalog entry keeps drawing |
| `ACTION_BOOT_COMPLETED` | `WidgetUpdateReceiver` | Cheap insurance that a device coming back up renders current state |

Theme changes are not an explicit trigger: `GlanceTheme`'s DayNight providers handle light/dark,
and a Material You wallpaper change repaints via the platform. If that proves unreliable in
testing, add it — one more action on the same receiver.

**Where the flag-driven update lives.** Today nothing in `:app` collects
`FeatureFlagRepository.flags()`; `ToolsViewModel` collects it per screen. Add an
application-scoped collector in `:app` that reconciles, writes back `retainedIds` when they differ,
and calls `updateAll()`. It must **not** live in `ToolsViewModel` — the widget's correctness cannot
depend on whether the user visited the home screen.

`ACTION_APPWIDGET_UPDATE` is handled by `GlanceAppWidgetReceiver` itself and needs no extra work.

---

## 8. Launch path

### 8.1 Intent

Widget tiles use `actionStartActivity` with an explicit intent:

```kotlin
object WidgetLaunch {
    const val EXTRA_TOOL_ID = "com.minion.scaffold.widget.EXTRA_TOOL_ID"
}
```

built against a `ComponentName` the app supplies (see the boundary note below), with
`FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP`.

**Why not deep links.** Navigation deep links would need `MainActivity` to accept an app-scheme
VIEW intent — a public surface any installed app can fire — plus a `deepLinks` declaration on every
route. An explicit component intent is reachable only by this app.

**Why not a serialized route in the extra.** Type-safe end to end, but it puts a kotlinx-serialized
payload through a boundary R8 processes — the enum-renaming trap `ScanPurpose` documents — and it
would make `:feature:widget` depend on the serializer. A string id has neither problem.

> **Boundary note.** `:feature:widget` cannot see `MainActivity` — a feature module may not depend
> on `:app`. Declare a `WidgetLaunchIntentFactory` interface in `:feature:widget` and bind it in
> `:app`. The widget then knows only "the thing that opens tools", the same shape as
> `FeatureFlagRepository` keeping Firebase out of the features.

### 8.2 Resolution in `:app`

`MainActivity` reads the extra in `onCreate` **and** `onNewIntent` (it is `singleTop`), resolves it
through a pure function, and hands the result to `AppNavHost` as an initial navigation request:

```kotlin
fun resolveWidgetRoute(
    toolId: String?,
    catalog: List<ToolDescriptor>,
    flags: FeatureFlags,
): AppRoute? =
    toolId
        ?.let { id -> catalog.firstOrNull { it.id == id } }
        ?.takeIf { flags.isEnabled(it.id) }
        ?.route
```

`null` — absent extra, unknown id, or a tool whose flag is currently off — means "do nothing
special", and the app opens on `ToolsRoute` as usual. That covers both the greyed tile's tap and a
stale intent: neither opens something the console has withheld.

**The extra must be consumed exactly once.** Clear it (`intent.removeExtra`) after handling, or a
configuration change re-navigates the user into the tool they just backed out of.

### 8.3 Back stack

On a widget launch the graph is reset to `ToolsRoute` and the target route pushed on top:

```kotlin
navController.navigate(route) {
    popUpTo(ToolsRoute) { inclusive = false }
    launchSingleTop = true
}
```

Back from a widget-launched tool always lands on the tools home, then exits. A user mid-task on
another screen loses that screen's back stack — accepted, because "the widget is a shortcut into
the app" is a clearer model than a back button that walks through history the user never chose,
and repeated widget taps would otherwise stack duplicates of the same tool.

### 8.4 Permissions

None. Camera (`qr-scan`, `qr-edit`, `ocr`), microphone (`sound-meter`) and location
(`speedometer`, `weather`) are all gated by the target screen's existing `PermissionState` flow
with its own rationale UI. A cold launch into a permission prompt is the same experience as
tapping the tile in-app.

The widget cannot request permissions and gets no broadcast when one changes, so any tile-level
permission marker would be stale most of the time. Not attempted.

---

## 9. Configuration screen

### 9.1 Route and placement

`WidgetSettingsRoute` — a `@Serializable data object` in `:core:navigation`. Screen, contract and
ViewModel live in `:feature:tools`, `internal`, registered by its own navigation file, matching the
sub-screen convention every other feature follows (`qrScanSettingsScreen`, `ocrSettingsScreen`, …).

**Why the shared types are in `:core:data`.** The screen needs `PinnedToolsRepository`, and
`:feature:tools` may not depend on `:feature:widget`. `:core:data` exists for exactly this — data
shared *between* features — and its README has been waiting for the second consumer that makes the
promotion legitimate. The interface, `PinnedTool` and `reconcilePinnedTools` go there; the
DataStore implementation and its Hilt binding stay in `:feature:widget`.

### 9.2 Entry point

An `IconButton` on the trailing edge of `HomeHeader` in `ToolsScreen`, with a widgets glyph,
emitting a new `ToolsIntent.WidgetSettingsSelected` → `ToolsEffect.OpenWidgetSettings` →
an `onOpenWidgetSettings` lambda passed down from `AppNavHost`.

### 9.3 Layout

```
┌─────────────────────────────────────────┐
│  Quick access widget            [Add ⊞] │   ← pin-to-home, when supported
│  Choose up to 5 tools. Drag to reorder. │
├─────────────────────────────────────────┤
│  ON THE WIDGET                     3/5  │   ← AppTextStyles.eyebrow
│  ⠿ [icon] QR Scan                    ☑  │   ← draggable
│  ⠿ [icon] Bubble Level               ☑  │
│  ⠿ [icon] Sound Meter                ☑  │
│           currently unavailable          │
├─────────────────────────────────────────┤
│  ALL TOOLS                              │
│    [icon] QR Create                  ☐  │
│    [icon] Wi-Fi QR                   ☐  │
│    …                                    │
└─────────────────────────────────────────┘
```

- Pinned block on top in widget order; the rest below in catalog order.
- A pinned-but-unavailable row shows a caption from the strings file and keeps its checkbox
  enabled — unpinning is how the slot is freed.
- The counter (`3/5`) is a formatted string resource (`%1$d/%2$d`), not interpolation.
- Every `LazyColumn` item gets a stable `key` (the tool id).

### 9.4 Cap behaviour

At five selected, every unselected row's checkbox is disabled and a caption reads that the widget
holds five tools and one must be removed first. A FIFO eviction was rejected: "oldest" is invisible
in a checkbox list, so a user reordering their picks could lose one without noticing.

### 9.5 Reorder

Hand-rolled drag-and-drop on the pinned block: `Modifier.pointerInput` with
`detectDragGesturesAfterLongPress`, an offset-tracking reorder state, and item translation. Drop
commits a `WidgetSettingsIntent.Reorder(from, to)`.

This is the most expensive choice in the spec — roughly 150 lines of pointer plumbing in an
otherwise trivial screen, and the kind of code that breaks quietly on a Compose upgrade. Two
requirements make it non-optional to do properly:

- **Accessibility.** A drag gesture is invisible to TalkBack. The pinned rows must carry explicit
  `semantics { customActions = … }` — "Move up" / "Move down" — dispatching the same `Reorder`
  intent. Without this, ordering does not exist for screen-reader users.
- **Testability.** All ordering logic lives in the ViewModel as `Reorder(from, to)`. The gesture
  layer only computes indices. Reordering is therefore unit-tested without touching gesture code.

### 9.6 Pin-to-home

When `AppWidgetManager.isRequestPinAppWidgetSupported` is true (API 26+, launcher-dependent), show
an "Add to home screen" button calling `requestPinAppWidget`. Hidden entirely otherwise — a button
that does nothing on some launchers is worse than no button.

### 9.7 Empty list

Legal. The widget renders a single full-width "Choose tools" tile that opens this screen, so it is
never a dead rectangle. No minimum is enforced: a disabled last checkbox with no explanation reads
as a bug.

---

## 10. Analytics

One event, logged in `:app` at the point the widget extra resolves:

| Event | Params |
|---|---|
| `widget_tool_opened` | `tool_id` (string) |

`FirebaseAnalytics` is already bound in `:app`'s `FirebaseModule`, and the resolution point is
already there — a one-line addition. Config events (pin/unpin) are deliberately not logged: which
tools a user pins is closer to a private arrangement than tool usage is.

Logged in `:app`, never in `:feature:widget` — the same rule that keeps Firebase out of every
feature.

---

## 11. Testing

Per the repo's culture of testing what has a checkable ground truth in a JVM test.

### 11.1 `reconcilePinnedTools` — `:core:data`

The only genuinely tricky logic, and pure. Cases:

- Unknown id is dropped from both `tools` and `retainedIds`.
- Flag-disabled id is retained and surfaces as `isAvailable = false`.
- Order is preserved through both outputs.
- Duplicates collapse to first occurrence.
- More than five stored truncates to five.
- Empty stored list yields an empty result — *not* the seed. Seeding is the repository's job;
  conflating them makes "user unpinned everything" unrepresentable.
- Every tool disabled → five unavailable tiles, nothing pruned.

`FeatureFlags` is a `fun interface`, so each case substitutes a lambda rather than a mock.

### 11.2 `WidgetSettingsViewModel` — `:feature:tools`

Standard `MviViewModel` test against a fake `PinnedToolsRepository` (living in that feature's
`src/test`, per the repo convention):

- Selecting at the cap is a no-op and leaves state unchanged.
- Deselecting frees a slot and re-enables the unselected rows.
- `Reorder(from, to)` produces the expected order for every in-range pair, and is a no-op out of
  range.
- Each mutation persists exactly once.
- An unavailable pinned tool renders in the pinned block, not the catalog block.

### 11.3 `resolveWidgetRoute` — `:app`

The launch path's weak point. Exhaustive over the catalog, plus `null` id, empty string, unknown
id, and an id whose flag is off — all must yield `null` rather than throwing.

### 11.4 Not tested

Glance rendering. `androidx.glance:glance-testing` could assert the composition, but no module in
this repo currently has an `androidTest` source set, and the composition is a thin projection of
`ReconcileResult`, which is fully covered. Revisit if the widget layout grows conditional logic of
its own.

---

## 12. UI token compliance

The widget and the config screen are held to CLAUDE.md's no-literals rule like any other screen:

- Tile labels, the empty-state prompt, the counter, the unavailable caption, the cap explanation
  and the widget's picker description all live in `strings.xml`.
- Tile size, icon size, row gap, corner radius, the alpha for an unavailable tile and the
  drag-handle hit area live in `dimens.xml` — including the values that look "too small to
  tokenize".
- The Glance colour bridge is the only place a colour is named, and it maps app palette entries,
  never hex.
- Label typography comes from `GlanceTheme`'s text styles; the config screen's eyebrow headings use
  `AppTextStyles.eyebrow`, not a `.copy(letterSpacing = …)`.

---

## 13. Implementation order

1. **`:core:toolcatalog`** — create the module, move `Tool`→`ToolDescriptor`, `ToolCategory` and
   `ToolCatalog`, move the 28 strings, add the 14 vector drawables and the `widgetIconRes` field.
   Update `:feature:tools` to consume it. `./gradlew build` must be green here before anything else
   starts.
2. **`:core:data`** — `PinnedTool`, `PinnedToolsRepository`, `reconcilePinnedTools`, plus its
   tests. Pure logic, no Android, no UI.
3. **`:feature:widget` plumbing** — module, DataStore implementation, Hilt module,
   `WidgetEntryPoint`, `WidgetLaunchIntentFactory` interface.
4. **Glance widget** — `QuickAccessWidget`, receiver, `WidgetGlanceTheme` (plus the
   `ColorProviders` bridge in `:core:designsystem`), provider XML, previews, manifest.
5. **Launch path** — `:app` binds the intent factory, `MainActivity` reads and clears the extra,
   `AppNavHost` accepts an initial route, analytics event, `resolveWidgetRoute` tests.
6. **Config screen** — route, contract, ViewModel and tests first; then the checkbox list and cap;
   then drag-reorder with its semantics actions; then the pin-to-home button. `HomeHeader` icon
   button last.
7. **Update triggers** — `WidgetUpdateReceiver` and the app-scoped flag collector with writeback.
8. **Backup rules** — both XML files.
9. **CLAUDE.md** — module count (31 → 33), the module-graph block, the `:core:data` entry now that
   it holds something, and a *Things that will bite you* entry for the
   DataStore-name-in-three-places trap.

---

## 14. Open items

Non-blocking, but settle these during implementation.

- **Glance version.** Pin the current stable at implementation time and confirm
  `GlanceTheme.colors` dynamic providers behave correctly on the minSdk-29 path.
- **Icon export.** The 14 drawables must be visually identical to their `Icons.Filled.*`
  counterparts. Export from the same Material set at the same optical size, and note the source in
  each file's comment so a later change can be repeated.
- **Dual icon fields.** `ToolDescriptor` carrying both `icon: ImageVector` and `widgetIconRes: Int`
  means a reviewer can change one and not the other. Consider a follow-up that drops `ImageVector`
  entirely and renders the drawable via `painterResource` on the tools screen too — deferred here
  to keep this change's diff bounded.
- **Backup narrowing.** Adding an `<include>` to the currently-empty `full-backup-content` changes
  backup from implicit-everything to this-file-only. Confirm nothing else depends on the current
  behaviour.
- **Preview drift.** `widget_preview.xml` duplicates the Glance layout by hand and will diverge.
  Accepted; revisit if the widget layout changes more than once.
- **Default seed.** See §6.4 — catalog order gives a create-heavy strip. Revisit with usage data
  from `widget_tool_opened`.
