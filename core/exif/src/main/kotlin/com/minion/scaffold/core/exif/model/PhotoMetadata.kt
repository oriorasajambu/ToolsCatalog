package com.minion.scaffold.core.exif.model

/**
 * What was found in a photo, ranked by how much of the owner it gives away.
 *
 * Lives in the pure module even though the *extraction* is Android-side, because the ranking is the
 * opinionated part and opinions deserve tests. Which tags count as a location leak is a judgement,
 * and a judgement that silently changed would quietly alter what the tool warns people about.
 */
data class PhotoMetadata(
    /** The metadata grouped into exposure bands, worst first. */
    val bands: List<MetadataBand>,
    /** Tags that exist but do not fall into a band. Shown collapsed, never hidden. */
    val other: List<MetadataEntry>,
    /**
     * An embedded preview image, if one is present.
     *
     * Called out separately because it is the least understood item in the list: a thumbnail is
     * generated when the photo is taken and is *not* always regenerated when the photo is edited, so
     * a cropped or redacted image can carry a small copy of what it was before.
     */
    val thumbnail: EmbeddedThumbnail?,

    /**
     * The position, in decimal degrees, when one was recorded.
     *
     * Typed rather than left to be fished back out of the [bands] by matching a label string. The
     * map action needs these two numbers specifically, and a rename of a display label should not be
     * able to silently disconnect a button.
     */
    val coordinates: Coordinates?,
) {

    /** Whether the photo carries any metadata at all. */
    val hasAnything: Boolean get() = bands.isNotEmpty() || other.isNotEmpty() || thumbnail != null

    /** The most exposing band present, for the one-line verdict at the top of the screen. */
    val worstExposure: Exposure?
        get() = bands.minByOrNull { it.category.exposure.ordinal }?.category?.exposure
}

/**
 * One category of metadata and the entries found in it.
 *
 * @property category The category these entries belong to.
 * @property entries  The individual tags found in this category.
 */
data class MetadataBand(val category: MetadataCategory, val entries: List<MetadataEntry>)

/**
 * Decimal degrees, as the only form worth showing or handing to a map.
 *
 * @property latitude  Latitude in decimal degrees.
 * @property longitude Longitude in decimal degrees.
 */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * One metadata tag, as it will be shown.
 *
 * @property label The human-readable tag name.
 * @property value The tag's value, formatted for display.
 */
data class MetadataEntry(val label: String, val value: String)

/**
 * A preview image embedded in the photo.
 *
 * @property byteCount The thumbnail's size in bytes.
 * @property width     The thumbnail's width in pixels.
 * @property height    The thumbnail's height in pixels.
 */
data class EmbeddedThumbnail(val byteCount: Int, val width: Int, val height: Int)

/**
 * How much a category gives away, worst first.
 *
 * Ordinal order is load-bearing — [PhotoMetadata.worstExposure] takes the minimum — so entries are
 * ordered by severity rather than alphabetically or by convenience.
 */
enum class Exposure { Identifying, Sensitive, Descriptive }

/**
 * The kinds of metadata the tool reports on, each carrying how much it gives away.
 *
 * Categories rather than raw tag names: a photo can carry dozens of separate GPS tags, and listing
 * them one by one buries the single fact that matters, which is that the location is in there.
 *
 * @property exposure How much this category reveals about the person rather than the camera.
 */
enum class MetadataCategory(val exposure: Exposure) {

    /**
     * Where the photo was taken, to a few metres.
     *
     * The reason this tool exists. Everything else on this list is a nuisance by comparison.
     */
    Location(Exposure.Identifying),

    /** Which device took it, down to a body or lens serial number on some cameras. */
    Device(Exposure.Identifying),

    /** When it was taken, to the second, usually with a timezone offset. */
    Time(Exposure.Sensitive),

    /** Who made or edited it, and with what. Names and copyright strings live here. */
    Provenance(Exposure.Sensitive),

    /** Exposure, aperture, focal length. Reveals the kind of camera, not the person. */
    Capture(Exposure.Descriptive),
}
