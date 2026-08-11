package com.minion.scaffold.feature.exifstrip.presentation

import android.net.Uri
import com.minion.scaffold.core.exif.model.MetadataKind
import com.minion.scaffold.core.exif.model.PhotoMetadata
import com.minion.scaffold.core.exif.model.SegmentSummary
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.exifstrip.domain.CleanCopyExporter
import com.minion.scaffold.feature.exifstrip.domain.ExifStripPreferencesRepository
import com.minion.scaffold.feature.exifstrip.domain.ExportResult
import com.minion.scaffold.feature.exifstrip.domain.InspectedPhoto
import com.minion.scaffold.feature.exifstrip.domain.InspectionResult
import com.minion.scaffold.feature.exifstrip.domain.ObserveKeepColourProfileUseCase
import com.minion.scaffold.feature.exifstrip.domain.PhotoInspector
import com.minion.scaffold.feature.exifstrip.domain.SetKeepColourProfileUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExifStripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val inspector = mockk<PhotoInspector>()
    private val exporter = mockk<CleanCopyExporter>(relaxed = true)
    private val preferences = FakePreferences()

    /** `Uri` is Android, so it is mocked — nothing under test does anything but pass it along. */
    private val uri = mockk<Uri>(relaxed = true)

    private fun viewModel() = ExifStripViewModel(
        photoInspector = inspector,
        exporter = exporter,
        observeKeepColourProfile = ObserveKeepColourProfileUseCase(preferences),
        setKeepColourProfile = SetKeepColourProfileUseCase(preferences),
    )

    @Test
    fun `a photo with no metadata loads and says so`() = runTest {
        givenInspection(metadata(bands = emptyList()))
        givenProbe(null)

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Loaded
        assertFalse(content.metadata.hasAnything)
        assertTrue(viewModel.state.value.canExport)
    }

    /**
     * A HEIC is still worth inspecting, and the conversion is offered rather than an export.
     *
     * The metadata reads perfectly on a container the tool cannot take apart, and seeing the GPS is
     * most of the value even when the lossless path is unavailable.
     */
    @Test
    fun `an unsupported container offers conversion instead of a lossless export`() = runTest {
        givenInspection(metadata())
        givenProbe(StripFailure.UnsupportedContainer("heic"))

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Loaded
        assertEquals("heic", content.convertibleFormat)
        assertFalse("a lossless export must not be offered", viewModel.state.value.canExport)
        assertTrue(viewModel.state.value.canConvert)
    }

    @Test
    fun `something that is not an image fails rather than loading`() = runTest {
        givenInspection(metadata())
        givenProbe(StripFailure.NotAnImage("unknown"))

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Failed
        assertEquals(ExifStripState.FailureReason.NotAnImage, content.reason)
    }

    @Test
    fun `a file too large to hold is reported as such`() = runTest {
        coEvery { inspector.inspect(uri) } returns
            InspectionResult.TooLarge(200L * 1024 * 1024)

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Failed
        assertEquals(ExifStripState.FailureReason.TooLarge, content.reason)
    }

    /**
     * **The most important test here.**
     *
     * A verification failure blocks the export outright — the file is not offered and no share URI
     * reaches the state. Presenting it with a warning would hand someone a photo with its GPS intact
     * under a heading saying it was clean, which is worse than failing.
     */
    @Test
    fun `a failed verification blocks the export entirely`() = runTest {
        givenInspection(metadata())
        givenProbe(null)
        coEvery { exporter.export(any(), any()) } returns
            ExportResult.VerificationFailed(
                listOf(SegmentSummary(MetadataKind.Exif, "APP1", 400)),
            )

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()
        viewModel.onIntent(ExifStripIntent.ExportRequested)
        advanceUntilIdle()

        assertNull("no shareable file may survive a failed check", viewModel.state.value.export)
        val content = viewModel.state.value.content as ExifStripState.Content.Failed
        assertEquals(ExifStripState.FailureReason.VerificationFailed, content.reason)
    }

    @Test
    fun `a successful export carries what was removed and kept`() = runTest {
        givenInspection(metadata())
        givenProbe(null)
        coEvery { exporter.export(any(), any()) } returns ExportResult.Success(
            uri = uri,
            fileName = "photo.jpg",
            byteCount = 900,
            originalByteCount = 1400,
            removed = listOf(SegmentSummary(MetadataKind.Exif, "APP1", 500)),
            retained = listOf(SegmentSummary(MetadataKind.IccProfile, "APP2", 100)),
            trailing = null,
            recompressed = false,
        )

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()
        viewModel.onIntent(ExifStripIntent.ExportRequested)
        advanceUntilIdle()

        val export = viewModel.state.value.export!!
        assertEquals(500, export.bytesSaved)
        assertEquals(MetadataKind.Exif, export.removed.single().kind)
        assertEquals(MetadataKind.IccProfile, export.retained.single().kind)
        assertFalse(export.recompressed)
    }

    /**
     * Picking a second photo drops the first one's export.
     *
     * The share button pointing at the *previous* photo's clean copy would be quietly catastrophic:
     * the user would send a file they never inspected, believing they had.
     */
    @Test
    fun `picking another photo clears the previous export`() = runTest {
        givenInspection(metadata())
        givenProbe(null)
        coEvery { exporter.export(any(), any()) } returns ExportResult.Success(
            uri = uri,
            fileName = "photo.jpg",
            byteCount = 10,
            originalByteCount = 20,
            removed = emptyList(),
            retained = emptyList(),
            trailing = null,
            recompressed = false,
        )

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()
        viewModel.onIntent(ExifStripIntent.ExportRequested)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.export != null)

        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        assertNull(viewModel.state.value.export)
    }

    @Test
    fun `clearing drops both the photo and the export`() = runTest {
        givenInspection(metadata())
        givenProbe(null)

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        viewModel.onIntent(ExifStripIntent.Cleared)
        advanceUntilIdle()

        assertEquals(ExifStripState.Content.Empty, viewModel.state.value.content)
        assertNull(viewModel.state.value.export)
    }

    /** A filename containing a date is flagged, because the export renames and should say why. */
    @Test
    fun `a dated filename is detected`() = runTest {
        givenInspection(metadata(), displayName = "IMG_20240115_143022.jpg")
        givenProbe(null)

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Loaded
        assertTrue(content.fileNameCarriesDate)
    }

    @Test
    fun `a plain filename is not flagged`() = runTest {
        givenInspection(metadata(), displayName = "holiday.jpg")
        givenProbe(null)

        val viewModel = viewModel()
        viewModel.onIntent(ExifStripIntent.PhotoPicked(uri))
        advanceUntilIdle()

        val content = viewModel.state.value.content as ExifStripState.Content.Loaded
        assertFalse(content.fileNameCarriesDate)
    }

    // region Helpers

    private fun givenInspection(metadata: PhotoMetadata, displayName: String? = null) {
        coEvery { inspector.inspect(uri) } returns InspectionResult.Success(
            InspectedPhoto(
                uri = uri,
                bytes = ByteArray(16),
                displayName = displayName,
                metadata = metadata,
                orientation = 1,
            ),
        )
    }

    private fun givenProbe(failure: StripFailure?) {
        coEvery { exporter.probe(any(), any()) } returns failure
    }

    private fun metadata(bands: List<com.minion.scaffold.core.exif.model.MetadataBand> = emptyList()) =
        PhotoMetadata(bands = bands, other = emptyList(), thumbnail = null, coordinates = null)

    private class FakePreferences : ExifStripPreferencesRepository {
        private val keep = MutableStateFlow(true)
        override val keepColourProfile: Flow<Boolean> = keep
        override suspend fun setKeepColourProfile(keep: Boolean) {
            this.keep.value = keep
        }
    }

    // endregion
}
