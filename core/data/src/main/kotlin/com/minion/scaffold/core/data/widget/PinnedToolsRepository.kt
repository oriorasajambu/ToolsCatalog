package com.minion.scaffold.core.data.widget

import kotlinx.coroutines.flow.Flow

/**
 * The tools pinned to the home-screen widget, in the order they are drawn.
 *
 * One global list rather than one per widget instance: every instance renders the same strip, so
 * there is nothing to keep in sync and no per-instance configuration activity to write.
 *
 * Ids rather than routes. An id is a stable string the Firebase console already keys against
 * (`feature_<id>_enabled`); a serialized route in persistent storage would be a second thing R8
 * could rename out from under a stored value. Ids are also what keeps `qr-scan` and `qr-edit`
 * distinguishable when both resolve to the same route.
 *
 * Declared here in `:core:data` rather than in the widget, because `:feature:tools` owns the
 * configuration screen and one feature may not depend on another. Implemented by the widget's own
 * DataStore.
 */
interface PinnedToolsRepository {

    /**
     * The pinned ids as stored, in display order.
     *
     * @return A [Flow] that emits the current list and again on every write.
     */
    val pinnedIds: Flow<List<String>>

    /**
     * Replaces the list wholesale.
     *
     * Capped at [MAX_PINNED_TOOLS] and de-duplicated on write, so a caller cannot persist a list
     * the widget would have to truncate at render time.
     *
     * @param ids The ids to pin, in display order.
     */
    suspend fun setPinned(ids: List<String>)

    /**
     * A one-shot read, for the widget's render pass.
     *
     * The widget renders once per update rather than collecting, so it awaits a single value
     * instead of holding a subscription for the lifetime of a broadcast.
     *
     * @return The pinned ids in display order.
     */
    suspend fun currentPinnedIds(): List<String>
}
