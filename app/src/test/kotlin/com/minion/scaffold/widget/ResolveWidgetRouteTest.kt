package com.minion.scaffold.widget

import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The launch path's weak point: everything else about a widget tap is a lookup, and this is the
 * one place a bad id turns into either a screen or a crash.
 *
 * Exhaustive over the catalog rather than a sample. A tool added without a route, or with an id
 * that does not match the one the widget stores, fails here rather than on someone's home screen.
 */
class ResolveWidgetRouteTest {

    @Test
    fun `every catalog tool resolves to its own route when enabled`() {
        ToolCatalog.entries.forEach { tool ->
            val route = resolveWidgetRoute(tool.id, ToolCatalog.entries, allEnabled)

            assertNotNull("no route for ${tool.id}", route)
            assertEquals(tool.route, route)
        }
    }

    @Test
    fun `a null id resolves to null`() {
        assertNull(resolveWidgetRoute(null, ToolCatalog.entries, allEnabled))
    }

    @Test
    fun `an empty id resolves to null rather than matching anything`() {
        assertNull(resolveWidgetRoute("", ToolCatalog.entries, allEnabled))
    }

    @Test
    fun `an unknown id resolves to null`() {
        assertNull(resolveWidgetRoute("no-such-tool", ToolCatalog.entries, allEnabled))
    }

    @Test
    fun `a known id whose flag is off resolves to null`() {
        ToolCatalog.entries.forEach { tool ->
            val route = resolveWidgetRoute(
                toolId = tool.id,
                catalog = ToolCatalog.entries,
                flags = FeatureFlags { it != tool.id },
            )

            assertNull("${tool.id} opened while withheld", route)
        }
    }

    @Test
    fun `an empty catalog resolves to null rather than throwing`() {
        assertNull(resolveWidgetRoute(ToolCatalog.entries.first().id, emptyList(), allEnabled))
    }

    @Test
    fun `qr-scan and qr-edit resolve to different routes`() {
        // The two share a destination and are told apart only by their argument, which is the
        // reason the widget stores ids rather than routes. If these ever collapse to the same
        // value, a widget tile silently opens the wrong one of the pair.
        val scan = resolveWidgetRoute("qr-scan", ToolCatalog.entries, allEnabled)
        val edit = resolveWidgetRoute("qr-edit", ToolCatalog.entries, allEnabled)

        assertNotNull(scan)
        assertNotNull(edit)
        assertEquals(false, scan == edit)
    }

    private companion object {
        val allEnabled = FeatureFlags { true }
    }
}
