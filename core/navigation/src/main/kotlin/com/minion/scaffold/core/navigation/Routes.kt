package com.minion.scaffold.core.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * Marker for every destination in the app.
 *
 * Routes are declared here, in a module that owns no UI, so that a feature can navigate to
 * another feature's screen while depending only on the contract. `:feature:a` constructs a
 * [AppRoute]; `:feature:b` registers a composable for it; neither knows the other exists.
 *
 * Type-safe navigation (navigation-compose 2.8+) serialises these directly — no string route
 * templates, no argument keys to misspell, and `savedStateHandle.toRoute<T>()` in the ViewModel
 * gives back the same object.
 *
 * Route arguments must be ids and primitives, never domain models. A route carrying a `User`
 * would mean `:core:navigation` depends on `:core:domain`, and that the whole object had to
 * survive process death in the saved-state bundle.
 *
 * The route classes themselves need no `@Keep`. R8 may rename them, but a route's serial name is
 * read only by `composable<T>` and `navigate(T)` — both compiled in the same R8 run, so both see
 * the same renamed name. Argument names survive too: the generated serializer spells them out as
 * string constants, which is why reading one by [QrScanRoute.ARG_PURPOSE] is safe. The exception
 * is an enum argument, whose entries R8 renames out from under its serializer — see [ScanPurpose].
 */
sealed interface AppRoute

/**
 * The start destination: the list of every tool the app offers.
 *
 * Each entry in that list is an [AppRoute], which is why the tools feature can open any tool
 * while depending on none of them.
 */
@Serializable
data object ToolsRoute : AppRoute

/**
 * The EMV QR inquiry tool: scan or paste a payload, read it back decoded.
 *
 * [purpose] is an argument rather than a second route because both purposes are the same screen,
 * behaving identically right up to the moment a payload decodes. Two routes would mean two
 * registrations of one composable and a ViewModel that could not tell which one it was serving.
 *
 * @property purpose Why the scanner was opened — inspect a payload, or edit it.
 */
@Serializable
data class QrScanRoute(val purpose: ScanPurpose = ScanPurpose.Inspect) : AppRoute {

    companion object {
        /**
         * The key [purpose] is stored under in a `SavedStateHandle`.
         *
         * A ViewModel reads the raw argument by this name rather than calling
         * `savedStateHandle.toRoute()`. The typed decoder builds an `android.os.Bundle` on the
         * way, which does not exist in a JVM unit test — so using it would mean every ViewModel
         * test needed Robolectric to read one enum. The constant lives beside the property it
         * names so the two move together.
         *
         * The handle holds the **[ScanPurpose] itself**, not its name: navigation decodes the
         * argument before storing it. Reading it as a `String` compiles, passes a test that seeds
         * a `String`, and throws `ClassCastException` the first time a real navigation reaches
         * the screen.
         */
        const val ARG_PURPOSE = "purpose"
    }
}

/**
 * The scan tool's settings: which JSON schema template the export renders through.
 *
 * [payload] is optional and changes what the placeholder reference can say rather than what the
 * screen is. Opened from the scanner it is null and the reference lists each placeholder with its
 * description; opened from a report it carries that code, and the same list shows what each
 * placeholder is actually worth for it — which is the only way to discover which raw tags a given
 * payload even has.
 *
 * Capped by the caller at [QrCreateRoute]'s size, for the reason [TextToolsRoute.MAX_TEXT_LENGTH]
 * gives: a route argument rides in the saved-state `Bundle`.
 *
 * @property payload The scanned payload to resolve the reference against, or null for the plain
 *   reference.
 */
@Serializable
data class QrScanSettingsRoute(val payload: String? = null) : AppRoute {

    companion object {
        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_PAYLOAD = "payload"
    }
}

/**
 * Why the scanner was opened.
 *
 * `@Keep` because R8 renames enum entry fields, and this enum's generated serializer resolves
 * entries by name — so under minification decoding the `purpose` argument fails and navigating to
 * the scan screen throws. Latent only while `:app` sets `isMinifyEnabled = false`.
 *
 * The annotation rather than a `-keep` rule in `app/proguard-rules.pro`, which would fix the same
 * crash: it travels with the class, so it protects any consumer that minifies rather than this
 * repo's one `:app`, and it is what `MissingKeepAnnotation` looks for — a keep rule would leave
 * that warning standing and need a suppression on top of it. The cost is an annotations-only
 * artifact on this module's compile classpath and nothing at all on its runtime classpath.
 *
 * Covers this class only. Another enum added to this file needs its own `@Keep`; the route classes
 * do not, as nothing outside the same R8 run names them (see [AppRoute]).
 */
@Keep
@Serializable
enum class ScanPurpose {

    /** Decode it and show the report. */
    Inspect,

    /** Decode it and hand the payload to the editor. */
    Edit,
}

/**
 * The EMV QR authoring tool: fill in a merchant's details, get a scannable payload back.
 *
 * [payload] pre-fills the form, which is what turns this screen into the editor. A few hundred
 * characters of a primitive is exactly what a route argument is for, and putting it here rather
 * than in a shared holder means it survives process death with the rest of the back stack.
 *
 * @property payload An EMV payload to pre-fill the form with, or `null` to start blank.
 */
@Serializable
data class QrCreateRoute(val payload: String? = null) : AppRoute {

    companion object {
        /** The key [payload] is stored under in a `SavedStateHandle`. See [QrScanRoute.ARG_PURPOSE]. */
        const val ARG_PAYLOAD = "payload"
    }
}

/**
 * The Wi-Fi credential authoring tool: a network's details in, a code guests can scan out.
 *
 * A separate route from [QrCreateRoute] rather than a format argument on it, because the two
 * screens share no fields — one form serving both would have every field asking which format is
 * active.
 *
 * @property payload A `WIFI:` payload to pre-fill the form with, or `null` to start blank.
 */
@Serializable
data class WifiCreateRoute(val payload: String? = null) : AppRoute {

    companion object {
        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_PAYLOAD = "payload"
    }
}

/**
 * The web link authoring tool.
 *
 * @property payload A URL to pre-fill the form with, or `null` to start blank.
 */
@Serializable
data class UrlCreateRoute(val payload: String? = null) : AppRoute {

    companion object {
        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_PAYLOAD = "payload"
    }
}

/**
 * The contact card authoring tool — vCard 3.0.
 *
 * @property payload A vCard payload to pre-fill the form with, or `null` to start blank.
 */
@Serializable
data class VCardCreateRoute(val payload: String? = null) : AppRoute {

    companion object {
        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_PAYLOAD = "payload"
    }
}

/**
 * The text transform tool — encode, hash, reformat, re-case. No QR involved.
 *
 * [text] pre-fills the input, which is what lets the OCR tool hand its extraction straight here
 * instead of making the user copy and paste. Capped by the caller — see [MAX_TEXT_LENGTH].
 *
 * @property text Text to pre-fill the input with, or `null` to start blank.
 */
@Serializable
data class TextToolsRoute(val text: String? = null) : AppRoute {

    companion object {

        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_TEXT = "text"

        /**
         * How much text may ride in the route.
         *
         * Navigation arguments are serialised into the saved-state `Bundle`, which shares the
         * ~1MB Binder transaction ceiling with everything else being saved. A dense page of OCR'd
         * text is only a few kilobytes, so this ceiling is far above any realistic single
         * extraction — it exists so that a pathological one truncates visibly instead of crashing
         * the process with `TransactionTooLargeException` on the next configuration change.
         */
        const val MAX_TEXT_LENGTH = 32_000
    }
}

/** The on-device OCR tool: photograph or pick an image, get the text out. */
@Serializable
data object OcrRoute : AppRoute

/**
 * The OCR tool's own settings — which recognition engine reads a capture.
 *
 * A destination rather than a dialog because the choice needs room to explain itself: the engines
 * differ in speed, accuracy and on-device size, and a bare pair of radio buttons would give the
 * user nothing to choose on.
 */
@Serializable
data object OcrSettingsRoute : AppRoute

/** The generator tool — UUID, password, random hex. */
@Serializable
data object GenerateRoute : AppRoute

/** The bubble level and clinometer: is this surface level, and what is its slope. */
@Serializable
data object LevelRoute : AppRoute

/**
 * The level's guided two-point calibration.
 *
 * A destination rather than a dialog because the procedure asks the user to leave the phone alone on
 * a surface and turn it — something a sheet they have to reach past actively works against.
 */
@Serializable
data object LevelCalibrationRoute : AppRoute

/** The GPS speedometer and altimeter: how fast, how high, and where — with no network. */
@Serializable
data object SpeedometerRoute : AppRoute

/**
 * The speedometer's own settings — units, coordinate format, and what the numbers mean.
 *
 * A destination rather than controls on the main screen: three selectors on a display meant to be
 * read at a glance from a car mount is three too many, and the accuracy explanations need somewhere
 * to live that is not the speedometer.
 */
@Serializable
data object SpeedometerSettingsRoute : AppRoute

/** The metadata stripper: what a photo reveals, and a copy without it. */
@Serializable
data object ExifStripRoute : AppRoute

/**
 * The stripper's own settings — the colour-profile choice, and what the tool cannot promise.
 *
 * A destination rather than a panel because the second half needs room. The statement of what is
 * *not* removed matters more than the toggle, and a privacy tool implying a completeness it lacks is
 * worse than one that draws its line clearly.
 */
@Serializable
data object ExifStripSettingsRoute : AppRoute

/** The sound meter: how loud is it here, and what was the loudest it got. */
@Serializable
data object SoundMeterRoute : AppRoute

/**
 * The sound meter's own settings — the calibration offset, and what the tool does not claim.
 *
 * A destination rather than a panel on the meter. The offset is set once and then left alone, and a
 * slider beside a live reading invites adjusting it until the number looks agreeable — which feels
 * like calibrating and is the opposite of it.
 */
@Serializable
data object SoundMeterSettingsRoute : AppRoute

/**
 * The weather home screen: location permission gate plus the pinned current-location card.
 *
 * A `data object` like [ToolsRoute] rather than a route carrying arguments — there is nothing to
 * pass in; the screen resolves the device's own GPS fix.
 */
@Serializable
data object WeatherRoute : AppRoute

/**
 * One location's forecast — current conditions, notable-conditions banner, hourly strip, daily
 * list.
 *
 * [locationId] is `"current"` for the pinned GPS card or a saved location's id — the same key the
 * forecast cache is keyed by (`:feature:weather`'s `forecast_cache` table), so the detail screen
 * can look up coordinates from what was already fetched rather than needing them passed in too.
 *
 * @property locationId The cache key of the location to show — `"current"` or a saved location's id.
 */
@Serializable
data class WeatherDetailRoute(val locationId: String) : AppRoute {

    companion object {
        /** See [QrScanRoute.ARG_PURPOSE] for why this is read by name. */
        const val ARG_LOCATION_ID = "locationId"
    }
}

/** Place-name search, for adding a city to the saved-locations list. */
@Serializable
data object WeatherSearchRoute : AppRoute

/** The weather tool's own settings — the metric/imperial toggle. */
@Serializable
data object WeatherSettingsRoute : AppRoute

/**
 * Which tools sit on the home-screen widget, and in what order.
 *
 * A `data object`: the list it edits is global rather than per widget instance, so there is
 * nothing to identify and no argument to carry.
 *
 * Registered by `:feature:tools` rather than `:feature:widget`, even though it configures the
 * widget. It edits the tool catalog, the home screen already owns that, and the widget module has
 * no screens at all — the two share what they need through `:core:data`.
 */
@Serializable
data object WidgetSettingsRoute : AppRoute

/**
 * The checksum verifier: hash some text, and compare the digest against one you were given.
 *
 * A `data object` — the screen starts blank and there is nothing to hand it. A pre-filled variant
 * would be the moment to add a `text` argument, capped the way [TextToolsRoute] caps its own.
 */
@Serializable
data object ChecksumRoute : AppRoute
