package com.minion.scaffold

import android.app.Application
import android.content.res.Configuration
import com.minion.scaffold.widget.WidgetSynchroniser
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * The Hilt entry point. Every `@Inject` in the app resolves through the component this generates,
 * which is why it must be named in `AndroidManifest.xml`'s `android:name`.
 *
 * Keep it as close to empty as the work allows. Anything in `onCreate` runs before the first frame
 * on every cold start, including launches that never use it.
 *
 * The widget is the one thing here that genuinely cannot be lazy. Its correctness depends on a
 * stream nothing else collects, and on a configuration change no manifest receiver can hear — both
 * of which have to be heard whether or not the user opens a screen. Starting the collection is a
 * single `launchIn`; the work it does is suspending and off the critical path.
 */
@HiltAndroidApp
class AppApplication : Application() {

    @Inject
    lateinit var widgetSynchroniser: WidgetSynchroniser

    /**
     * Application-lifetime scope.
     *
     * `SupervisorJob` so one failed render cannot take the collection down with it — a widget that
     * stops updating after a single bad frame would look exactly like a widget that works.
     */
    private val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        widgetSynchroniser.start(scope)
    }

    /**
     * Light/dark is the case this exists for — see [WidgetSynchroniser.onConfigurationChanged].
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        widgetSynchroniser.onConfigurationChanged(scope)
    }

    /**
     * Not part of the Android lifecycle in practice — the process is killed rather than terminated
     * — but leaving a scope uncancelled in a class that owns one is the kind of thing that gets
     * copied into somewhere it does matter.
     */
    override fun onTerminate() {
        scope.cancel()
        super.onTerminate()
    }
}
