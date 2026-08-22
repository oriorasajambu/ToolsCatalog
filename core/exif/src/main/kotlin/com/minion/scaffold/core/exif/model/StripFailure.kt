package com.minion.scaffold.core.exif.model

/**
 * Why a file could not be planned.
 *
 * Sealed, so a new failure mode is a compile error everywhere it has to be handled — the same reason
 * `:core:emv`'s parse errors are. No user-facing text here: the feature maps these to strings, and
 * this module stays free of anything that would need translating.
 */
sealed interface StripFailure {

    /** The leading bytes match no container this module knows. */
    data class NotAnImage(val describedAs: String) : StripFailure

    /**
     * A real image, in a format that cannot be stripped without re-encoding it.
     *
     * [describedAs] carries the brand where one was found — `heic`, `avif` — because the feature
     * offers a conversion for exactly this case and the offer reads better when it names the format.
     */
    data class UnsupportedContainer(val describedAs: String) : StripFailure

    /**
     * The container is the right shape but its internal structure does not hold up.
     *
     * Reported with the offset it gave up at. Files arrive from a picker and may be truncated,
     * partially downloaded or deliberately malformed, and the parser must say so rather than
     * looping, reading out of bounds, or — worst — producing an output that looks fine.
     */
    data class Malformed(val offset: Int, val defect: Defect) : StripFailure

    /**
     * What was wrong with the file, as specifically as the parser is willing to say.
     *
     * Named cases rather than a message string, because the caller decides what to tell the user
     * and a developer-facing sentence is not something a screen can show.
     */
    enum class Defect {

        /** The file ends in the middle of a structure that promised more. */
        Truncated,

        /** A segment or chunk declared a length that cannot be right. */
        BadLength,

        /** The image data never ended — no EOI, or no IEND. */
        MissingEndMarker,

        /** A marker or chunk appeared somewhere the format does not allow. */
        UnexpectedStructure,
    }
}

/** The outcome of planning. */
sealed interface PlanResult {

    /**
     * A plan was produced.
     *
     * @property plan How to write the clean copy.
     */
    data class Success(val plan: StripPlan) : PlanResult

    /**
     * The file could not be planned.
     *
     * @property failure Why planning failed.
     */
    data class Failure(val failure: StripFailure) : PlanResult
}
