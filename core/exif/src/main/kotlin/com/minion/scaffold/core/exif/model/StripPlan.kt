package com.minion.scaffold.core.exif.model

/**
 * One step in rebuilding a file without its metadata.
 *
 * **The compressed image data is only ever named by a [Copy].** That is the whole point of planning
 * the work instead of doing it: there is no operation that transforms pixels, so no future edit to
 * this module can accidentally introduce a re-encode. The guarantee is held by the type rather than
 * by whoever reviews the next change.
 */
sealed interface StripOperation {

    /** Copy `[start, endExclusive)` from the input, verbatim. */
    data class Copy(val start: Int, val endExclusive: Int) : StripOperation {
        val length: Int get() = endExclusive - start
    }

    /**
     * Write these bytes, which came from nowhere in the input.
     *
     * Only ever a container header being rewritten, or the minimal orientation block — both short,
     * both fully determined, neither derived from image content.
     */
    class Insert(val bytes: ByteArray) : StripOperation {

        // Written out because a data class over a ByteArray compares by reference, which would make
        // two identical plans unequal and every test asserting on one quietly meaningless.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Insert && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()

        override fun toString(): String = "Insert(${bytes.size} bytes)"
    }
}

/**
 * What a block of metadata is, in terms a person can be shown.
 *
 * Format-agnostic: a JPEG `APP1` holding Exif, a PNG `eXIf` chunk and a WebP `EXIF` chunk are all
 * [Exif] here, because the user does not care which container spelling their camera used.
 */
enum class MetadataKind {

    /** Camera, exposure, timestamps, GPS. The reason this tool exists. */
    Exif,

    /**
     * The minimal block carrying only the orientation tag.
     *
     * Its own kind rather than [Exif], because the UI names what it kept and describing this as
     * "camera and location data" is the exact opposite of the truth — alarming in a privacy tool,
     * and found on a device saying precisely that.
     */
    Orientation,

    /** Adobe's XML metadata. Carries edit history, and sometimes a duplicate of the GPS. */
    Xmp,

    /** IPTC/Photoshop resource block — captions, credit, location names. */
    Iptc,

    /** A free-text comment, or a PNG text chunk. */
    Comment,

    /** Colour space description. Not identifying; see the retention note in the strip planner. */
    IccProfile,

    /** A creation or modification time recorded by the container itself. */
    Timestamp,

    /**
     * A block the allowlist did not recognise.
     *
     * Dropped by default and named in the report. Vendor blocks live here, and so do maker notes —
     * which is where cameras put body and lens serial numbers.
     */
    Unknown,
}

/** One block, as it will be described to the user. */
data class SegmentSummary(
    /** What kind of metadata this block is. */
    val kind: MetadataKind,
    /** The raw marker or chunk name — `APP7`, `tEXt`. Shown for [MetadataKind.Unknown]. */
    val name: String,
    /** The block's size in bytes. */
    val byteCount: Int,
)

/**
 * Data sitting after the end of the image, which no decoder reads.
 *
 * Precisely because nothing reads it, it is where things get hidden. **Samsung and Google motion
 * photos append a whole MP4 here** — several seconds of video and audio from around the moment of
 * the shot. Stripping the GPS tag and shipping that would be the largest hole this tool could leave,
 * inside a file it had just declared clean.
 */
/**
 * A run of bytes after the end of the image.
 *
 * @property byteCount How many trailing bytes there are.
 * @property kind      What the trailing data appears to be.
 */
data class TrailingData(val byteCount: Int, val kind: TrailingKind)

/**
 * What the bytes sitting after the image data appear to be.
 *
 * A guess by construction — trailing data carries no header announcing itself — so the two honest
 * answers are "a motion photo" and "something, this many bytes of it".
 */
enum class TrailingKind {

    /** An ISO base-media `ftyp` box was found — a motion photo, almost certainly. */
    EmbeddedVideo,

    /** Something else. Reported by size, since there is nothing honest to call it. */
    Unknown,
}

/**
 * How to write a clean copy of one file.
 *
 * [removed] and [retained] exist so the UI can say what happened rather than asserting success:
 * anything kept has to be named, which is what stops "stripped" quietly meaning "stripped of the
 * things we happened to think of".
 */
data class StripPlan(
    /** The container the output will be written in. */
    val container: ImageContainer,
    /** The copy/insert steps that rebuild the file, in order. */
    val operations: List<StripOperation>,
    /** The metadata blocks that will be dropped. */
    val removed: List<SegmentSummary>,
    /** The metadata blocks kept on purpose, e.g. orientation or a colour profile. */
    val retained: List<SegmentSummary>,
    /** Trailing data found after the image, or `null` when there is none. */
    val trailing: TrailingData?,
) {

    /** The size of the file this plan would write, in bytes. */
    val outputSize: Int
        get() = operations.sumOf { operation ->
            when (operation) {
                is StripOperation.Copy -> operation.length
                is StripOperation.Insert -> operation.bytes.size
            }
        }

    /** Whether there was anything to do. A photo with no metadata is worth saying so about. */
    val hasAnythingToRemove: Boolean get() = removed.isNotEmpty() || trailing != null
}
