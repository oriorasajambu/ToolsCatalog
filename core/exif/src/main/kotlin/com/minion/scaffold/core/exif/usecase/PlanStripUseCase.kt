package com.minion.scaffold.core.exif.usecase

import com.minion.scaffold.core.exif.jpeg.JpegStripPlanner
import com.minion.scaffold.core.exif.model.ImageContainer
import com.minion.scaffold.core.exif.model.PlanResult
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.exif.png.PngStripPlanner
import com.minion.scaffold.core.exif.webp.WebPStripPlanner
import javax.inject.Inject

/**
 * Works out how to write a clean copy of a file.
 *
 * The container is identified from the leading bytes, never from the file name or the `Uri`'s
 * declared MIME type. A picker will hand over a `.jpg` that is really a HEIC — phones rename on
 * export and messaging apps are worse — and a stripper that trusted the label would run a JPEG
 * parser over ISO base-media boxes and produce something between a corrupt file and a confident lie.
 *
 * @param orientation the EXIF orientation of the source, read by the caller. JPEG only; see
 *   `MinimalExif` for why the one tag survives and why it is synthesised rather than edited.
 * @param keepIcc whether to retain a JPEG colour profile. A user preference, because it trades a
 *   strictly smaller file against wide-gamut photos rendering correctly.
 */
class PlanStripUseCase @Inject constructor() {

    operator fun invoke(
        bytes: ByteArray,
        orientation: Int,
        keepIcc: Boolean,
    ): PlanResult = when (ImageContainer.detect(bytes)) {
        ImageContainer.Jpeg -> JpegStripPlanner.plan(bytes, orientation, keepIcc)
        ImageContainer.Png -> PngStripPlanner.plan(bytes)
        ImageContainer.WebP -> WebPStripPlanner.plan(bytes)

        null -> PlanResult.Failure(unsupported(bytes))
    }

    /**
     * Distinguishes "a picture this tool cannot strip" from "not a picture".
     *
     * Worth the effort because the two lead somewhere different: HEIC gets an offer to convert,
     * while a text file renamed to `.jpg` gets told what it actually is. Naming the format is also
     * simply more useful than "unsupported file" to someone whose phone shoots HEIC by default and
     * has no idea that is what happened.
     */
    private fun unsupported(bytes: ByteArray): StripFailure {
        val described = ImageContainer.describeUnsupported(bytes)

        return if (described in CONVERTIBLE_BRANDS) {
            StripFailure.UnsupportedContainer(described)
        } else {
            StripFailure.NotAnImage(described)
        }
    }

    private companion object {
        /**
         * ISO base-media brands that are real images, so worth offering a conversion for.
         *
         * `mif1` and `msf1` are the generic still and sequence brands; the rest are what phones
         * actually write.
         */
        val CONVERTIBLE_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "mif1", "msf1", "avif")
    }
}
