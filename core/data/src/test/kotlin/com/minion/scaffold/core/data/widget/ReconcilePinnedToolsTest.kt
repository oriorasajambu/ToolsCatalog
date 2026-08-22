package com.minion.scaffold.core.data.widget

import com.minion.scaffold.core.domain.featureflag.FeatureFlags
import com.minion.scaffold.core.toolcatalog.ToolCatalog
import com.minion.scaffold.core.toolcatalog.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run against the real [ToolCatalog] rather than a hand-built one.
 *
 * `AppRoute` is a sealed interface, so a fake descriptor cannot be constructed from another
 * module at all — but the shipped catalog is also the better fixture: it is ordered, it is longer
 * than the cap, and a test that pins `qr-scan` fails for a real reason if that id is ever renamed.
 */
class ReconcilePinnedToolsTest {

    @Test
    fun `an id with no catalog entry is dropped from both outputs`() {
        val result = reconcile(listOf(FIRST, "no-such-tool", SECOND))

        assertEquals(listOf(FIRST, SECOND), result.retainedIds)
        assertEquals(listOf(FIRST, SECOND), result.tools.ids())
    }

    @Test
    fun `a flag-disabled tool is retained and reported unavailable`() {
        val result = reconcile(listOf(FIRST, SECOND), FeatureFlags { it != SECOND })

        assertEquals(listOf(FIRST, SECOND), result.retainedIds)
        assertEquals(listOf(true, false), result.tools.map { it.isAvailable })
    }

    @Test
    fun `order is preserved through both outputs`() {
        val reversed = catalogIds.reversed().take(MAX_PINNED_TOOLS)

        val result = reconcile(reversed)

        assertEquals(reversed, result.retainedIds)
        assertEquals(reversed, result.tools.ids())
    }

    @Test
    fun `duplicates collapse to the first occurrence`() {
        val result = reconcile(listOf(SECOND, FIRST, SECOND))

        assertEquals(listOf(SECOND, FIRST), result.retainedIds)
    }

    @Test
    fun `more than the cap truncates to the cap`() {
        val result = reconcile(catalogIds)

        assertEquals(MAX_PINNED_TOOLS, result.tools.size)
        assertEquals(catalogIds.take(MAX_PINNED_TOOLS), result.retainedIds)
    }

    @Test
    fun `an empty stored list reconciles to empty rather than a seeded default`() {
        val result = reconcile(emptyList())

        assertTrue(result.tools.isEmpty())
        assertTrue(result.retainedIds.isEmpty())
    }

    @Test
    fun `every tool disabled prunes nothing and reports all unavailable`() {
        val pinned = catalogIds.take(MAX_PINNED_TOOLS)

        val result = reconcile(pinned, FeatureFlags { false })

        assertEquals(pinned, result.retainedIds)
        assertEquals(MAX_PINNED_TOOLS, result.tools.size)
        assertTrue(result.tools.none { it.isAvailable })
    }

    @Test
    fun `an id that only ever appears as a duplicate unknown still drops`() {
        val result = reconcile(listOf("no-such-tool", "no-such-tool"))

        assertTrue(result.retainedIds.isEmpty())
        assertTrue(result.tools.isEmpty())
    }

    @Test
    fun `the cap counts unavailable tools against it`() {
        val pinned = catalogIds.take(MAX_PINNED_TOOLS + 1)
        val onlyTheLast = pinned.last()

        val result = reconcile(pinned, FeatureFlags { it == onlyTheLast })

        // The only enabled tool is the sixth. If the cap counted availability it would survive;
        // it must not, because the cap counts pinned ids whether they are available or not.
        assertEquals(pinned.take(MAX_PINNED_TOOLS), result.retainedIds)
        assertTrue(result.tools.none { it.isAvailable })
    }

    private fun reconcile(
        storedIds: List<String>,
        flags: FeatureFlags = allEnabled,
    ): ReconcileResult = reconcilePinnedTools(storedIds, ToolCatalog.entries, flags)

    private fun List<PinnedTool>.ids(): List<String> = map { it.descriptor.id }

    private companion object {
        val allEnabled = FeatureFlags { true }
        val catalogIds: List<String> = ToolCatalog.entries.map(ToolDescriptor::id)
        val FIRST: String = catalogIds[0]
        val SECOND: String = catalogIds[1]
    }
}
