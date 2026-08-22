package com.minion.scaffold.feature.tools.presentation.widget

import com.minion.scaffold.core.data.widget.MAX_PINNED_TOOLS
import com.minion.scaffold.core.data.widget.PinnedToolsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An in-memory stand-in for the widget's DataStore.
 *
 * Lives in this feature's own `src/test` rather than `:core:testing`, per the repo convention: a
 * fake for one feature's repository is that feature's business.
 *
 * [writes] counts calls rather than recording only the latest value, because "persists exactly
 * once per mutation" is one of the things worth asserting — a ViewModel that reduced locally
 * *and* wrote would pass a final-value check and fail this one.
 */
internal class FakePinnedToolsRepository(
    initial: List<String> = emptyList(),
) : PinnedToolsRepository {

    private val ids = MutableStateFlow(initial)

    /** How many times [setPinned] has been called. */
    var writes: Int = 0
        private set

    override val pinnedIds: Flow<List<String>> = ids

    override suspend fun setPinned(ids: List<String>) {
        writes++
        this.ids.value = ids.distinct().take(MAX_PINNED_TOOLS)
    }

    override suspend fun currentPinnedIds(): List<String> = ids.value
}
