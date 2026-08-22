package com.minion.scaffold

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.minion.scaffold.core.designsystem.theme.AppTheme
import com.minion.scaffold.core.domain.featureflag.FeatureFlagRepository
import com.minion.scaffold.core.navigation.AppRoute
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.navigation.AppNavHost
import com.minion.scaffold.widget.WidgetLaunch
import com.minion.scaffold.widget.resolveWidgetRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single activity. Installs the splash screen, applies the theme, hands off to the nav graph.
 *
 * One activity, many composable destinations — screens are added to [AppNavHost], never as new
 * activities. A second activity would mean a second `ViewModelStore`, a second back stack and a
 * second place to remember to apply the theme.
 *
 * The one piece of screen-adjacent work that does belong here is the widget launch: an intent
 * arrives at an activity, not at a composable, and it arrives twice over — once through
 * [onCreate] on a cold start and once through [onNewIntent] when the task is already running.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var featureFlagRepository: FeatureFlagRepository

    @Inject
    lateinit var analytics: FirebaseAnalytics

    /**
     * Where a widget tap wants to land, once resolved.
     *
     * State rather than a value read during composition: resolving needs the flags, which is a
     * suspend read, and the answer arrives after the first frame.
     */
    private var widgetRoute by mutableStateOf<AppRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, and before setContent: the splash screen has to be installed
        // while the activity's theme is still the splash theme, or the system swaps to the app
        // theme first and the transition flickers.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleWidgetLaunch(intent)

        setContent {
            AppTheme {
                AppNavHost(
                    initialRoute = widgetRoute,
                    onInitialRouteHandled = { widgetRoute = null },
                )
            }
        }
    }

    /**
     * The activity is effectively single-top — one activity, always the top of its own task — and
     * the widget's intent carries `FLAG_ACTIVITY_SINGLE_TOP`, so a tap on a running app arrives
     * here rather than through [onCreate].
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetLaunch(intent)
    }

    /**
     * Resolves the tool id the intent carries, and consumes it.
     *
     * **The extra has to be removed once read.** The activity's intent outlives a configuration
     * change, so leaving it in place re-navigates the user into the tool they just backed out of,
     * every time the device is rotated.
     *
     * The analytics event is logged for what the user asked for rather than for what they got: an
     * id that resolves to nothing was still a tap on that tile, and losing those hides exactly the
     * case worth knowing about.
     */
    private fun handleWidgetLaunch(intent: Intent) {
        val toolId = intent.getStringExtra(WidgetLaunch.EXTRA_TOOL_ID) ?: return
        intent.removeExtra(WidgetLaunch.EXTRA_TOOL_ID)

        analytics.logEvent(WIDGET_TOOL_OPENED) {
            param(PARAM_TOOL_ID, toolId)
        }

        lifecycleScope.launch {
            val flags = featureFlagRepository.flags().first()
            widgetRoute = resolveWidgetRoute(toolId, ToolCatalog.entries, flags)
        }
    }

    private companion object {
        const val WIDGET_TOOL_OPENED = "widget_tool_opened"
        const val PARAM_TOOL_ID = "tool_id"
    }
}
