package com.minion.scaffold.feature.exifstrip.data

import androidx.core.content.FileProvider
import com.minion.scaffold.feature.exifstrip.R

/**
 * A `FileProvider` of this feature's own, existing purely so its class name is unique.
 *
 * **The manifest merger keys `<provider>` elements on `android:name`, not on `android:authorities`.**
 * So two library modules that both declare `androidx.core.content.FileProvider` collide even with
 * completely different authorities — the merge fails with a conflict on the authority attribute,
 * which points at the symptom and not the cause, and suggests `tools:replace`, which would silently
 * make one feature's provider serve the other's paths.
 *
 * A subclass that adds nothing is the standard answer: distinct `android:name`, no merge conflict,
 * and each provider keeps its own `FILE_PROVIDER_PATHS`. Passing the paths resource to the
 * superclass constructor also means the meta-data element is unnecessary.
 */
internal class ExifStripFileProvider : FileProvider(R.xml.exifstrip_file_paths)
