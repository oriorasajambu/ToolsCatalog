package com.minion.scaffold.feature.widget.glance

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.minion.scaffold.feature.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The Glance side of [WidgetUpdater]. The only place outside this package that knows Glance. */
internal class GlanceWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WidgetUpdater {

    override suspend fun updateAll() = QuickAccessWidget().updateAll(context)
}
