package com.minion.scaffold.feature.exifstrip.data

import androidx.exifinterface.media.ExifInterface
import com.minion.scaffold.core.exif.model.Coordinates
import com.minion.scaffold.core.exif.model.EmbeddedThumbnail
import com.minion.scaffold.core.exif.model.MetadataBand
import com.minion.scaffold.core.exif.model.MetadataCategory
import com.minion.scaffold.core.exif.model.MetadataEntry
import com.minion.scaffold.core.exif.model.PhotoMetadata
import javax.inject.Inject

/**
 * Turns an `ExifInterface` into the ranked view the screen shows.
 *
 * ## Read with this, strip with `:core:exif`
 *
 * `ExifInterface` is the right tool for *reading*: it handles JPEG, PNG, WebP and HEIC, and knows
 * the long tail of tags. It is the wrong tool for *removing* them. `saveAttributes` only understands
 * the tags it has definitions for, so XMP, IPTC, maker notes and MPF blocks survive a save — and a
 * stripper that leaves data behind while reporting success is worse than no stripper. Hence the
 * split: the platform reads, the pure module rewrites.
 *
 * ## The tag list is a judgement, so it is enumerated rather than derived
 *
 * Which tags count as identifying is an opinion about what harms someone, and opinions belong
 * written down. Anything not named here still appears — under "everything else" — so the ranking
 * decides prominence, never visibility.
 */
internal class ExifTagReader @Inject constructor() {

    fun read(exif: ExifInterface): PhotoMetadata {
        val coordinates = coordinatesOf(exif)

        val bands = CATEGORISED_TAGS.mapNotNull { (category, tags) ->
            val entries = buildList {
                // Decimal degrees rather than the stored form. Exif keeps position as three
                // rationals — "3/1,35/1,42876/1000" — which is correct, unreadable, and impossible
                // to compare against anything. The tool exists to make this legible.
                if (category == MetadataCategory.Location && coordinates != null) {
                    add(MetadataEntry("Latitude", formatDegrees(coordinates.latitude)))
                    add(MetadataEntry("Longitude", formatDegrees(coordinates.longitude)))
                }
                addAll(tags.mapNotNull { tag -> entryFor(exif, tag) })
            }
            if (entries.isEmpty()) null else MetadataBand(category, entries)
        }

        val named = CATEGORISED_TAGS.values.flatten().map { it.tag }.toSet()
        val other = OTHER_TAGS
            .filterNot { it.tag in named }
            .mapNotNull { tag -> entryFor(exif, tag) }

        return PhotoMetadata(
            bands = bands,
            other = other,
            thumbnail = thumbnailOf(exif),
            coordinates = coordinates,
        )
    }

    /**
     * The position in decimal degrees, or null when the photo carries none.
     *
     * `getLatLong` does the sign work that the raw tags leave to the caller: latitude and longitude
     * are stored as unsigned magnitudes with a separate N/S and E/W reference, so reading the
     * magnitudes alone puts every southern-hemisphere photo in the wrong half of the world.
     */
    private fun coordinatesOf(exif: ExifInterface): Coordinates? {
        val position = exif.latLong ?: return null
        return Coordinates(latitude = position[0], longitude = position[1])
    }

    private fun formatDegrees(value: Double) = "%.6f".format(value)

    /**
     * The orientation, or 1 when absent or unreadable.
     *
     * Falling back to "normal" rather than to "unknown" is deliberate: a file with no orientation tag
     * is displayed unrotated by every decoder, so normal is not a guess, it is what the absence
     * means.
     */
    fun orientationOf(exif: ExifInterface): Int = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )

    private fun entryFor(exif: ExifInterface, tag: TagLabel): MetadataEntry? {
        val raw = exif.getAttribute(tag.tag)?.trim().orEmpty()
        if (raw.isEmpty()) return null

        return MetadataEntry(tag.label, raw)
    }

    /**
     * The embedded preview, when there is one.
     *
     * Surfaced separately because it is the least understood item on the screen. A thumbnail is
     * generated when the photo is taken and is not always regenerated when the photo is edited, so a
     * cropped or redacted image can carry a small copy of what it looked like before — which has
     * embarrassed people who did the redacting carefully.
     */
    private fun thumbnailOf(exif: ExifInterface): EmbeddedThumbnail? {
        if (!exif.hasThumbnail()) return null

        val bytes = exif.thumbnailBytes ?: return null
        val dimensions = exif.getThumbnailRange()

        return EmbeddedThumbnail(
            byteCount = bytes.size,
            width = exif.getAttributeInt(ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH, 0),
            height = exif.getAttributeInt(ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH, 0),
        ).takeIf { dimensions != null || bytes.isNotEmpty() }
    }

    private data class TagLabel(val tag: String, val label: String)

    private companion object {

        /**
         * The tags worth ranking, in the order the screen shows them.
         *
         * A `LinkedHashMap` by construction, because the iteration order *is* the presentation
         * order: location first, because it is the reason the tool exists, and everything else is a
         * nuisance by comparison.
         */
        val CATEGORISED_TAGS: Map<MetadataCategory, List<TagLabel>> = linkedMapOf(
            MetadataCategory.Location to listOf(
                // Latitude and longitude are added separately, in decimal degrees — see `read`.
                TagLabel(ExifInterface.TAG_GPS_ALTITUDE, "Altitude"),
                TagLabel(ExifInterface.TAG_GPS_DATESTAMP, "GPS date"),
                TagLabel(ExifInterface.TAG_GPS_TIMESTAMP, "GPS time"),
                TagLabel(ExifInterface.TAG_GPS_IMG_DIRECTION, "Facing"),
                TagLabel(ExifInterface.TAG_GPS_AREA_INFORMATION, "Area"),
                TagLabel(ExifInterface.TAG_GPS_PROCESSING_METHOD, "Positioning method"),
            ),

            MetadataCategory.Device to listOf(
                TagLabel(ExifInterface.TAG_MAKE, "Camera make"),
                TagLabel(ExifInterface.TAG_MODEL, "Camera model"),
                TagLabel(ExifInterface.TAG_BODY_SERIAL_NUMBER, "Body serial number"),
                TagLabel(ExifInterface.TAG_LENS_MAKE, "Lens make"),
                TagLabel(ExifInterface.TAG_LENS_MODEL, "Lens model"),
                TagLabel(ExifInterface.TAG_LENS_SERIAL_NUMBER, "Lens serial number"),
            ),

            MetadataCategory.Time to listOf(
                TagLabel(ExifInterface.TAG_DATETIME_ORIGINAL, "Taken"),
                TagLabel(ExifInterface.TAG_DATETIME_DIGITIZED, "Digitised"),
                TagLabel(ExifInterface.TAG_DATETIME, "Modified"),
                TagLabel(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "Timezone"),
            ),

            MetadataCategory.Provenance to listOf(
                TagLabel(ExifInterface.TAG_SOFTWARE, "Software"),
                TagLabel(ExifInterface.TAG_ARTIST, "Artist"),
                TagLabel(ExifInterface.TAG_COPYRIGHT, "Copyright"),
                TagLabel(ExifInterface.TAG_USER_COMMENT, "Comment"),
                TagLabel(ExifInterface.TAG_IMAGE_DESCRIPTION, "Description"),
            ),

            MetadataCategory.Capture to listOf(
                TagLabel(ExifInterface.TAG_EXPOSURE_TIME, "Exposure"),
                TagLabel(ExifInterface.TAG_F_NUMBER, "Aperture"),
                TagLabel(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO"),
                TagLabel(ExifInterface.TAG_FOCAL_LENGTH, "Focal length"),
                TagLabel(ExifInterface.TAG_FLASH, "Flash"),
                TagLabel(ExifInterface.TAG_WHITE_BALANCE, "White balance"),
            ),
        )

        /** Everything else worth listing, shown collapsed beneath the ranked bands. */
        val OTHER_TAGS: List<TagLabel> = listOf(
            TagLabel(ExifInterface.TAG_IMAGE_WIDTH, "Width"),
            TagLabel(ExifInterface.TAG_IMAGE_LENGTH, "Height"),
            TagLabel(ExifInterface.TAG_ORIENTATION, "Orientation"),
            TagLabel(ExifInterface.TAG_X_RESOLUTION, "X resolution"),
            TagLabel(ExifInterface.TAG_Y_RESOLUTION, "Y resolution"),
            TagLabel(ExifInterface.TAG_RESOLUTION_UNIT, "Resolution unit"),
            TagLabel(ExifInterface.TAG_COLOR_SPACE, "Colour space"),
            TagLabel(ExifInterface.TAG_EXIF_VERSION, "Exif version"),
            TagLabel(ExifInterface.TAG_SCENE_CAPTURE_TYPE, "Scene type"),
            TagLabel(ExifInterface.TAG_METERING_MODE, "Metering"),
            TagLabel(ExifInterface.TAG_EXPOSURE_MODE, "Exposure mode"),
            TagLabel(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "Digital zoom"),
            TagLabel(ExifInterface.TAG_SUBJECT_DISTANCE, "Subject distance"),
        )
    }
}
