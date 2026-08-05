package com.minion.scaffold.feature.qrscan.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.minion.scaffold.core.emv.model.QrInquiryReport
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.core.navigation.QrScanRoute
import com.minion.scaffold.core.navigation.ScanPurpose
import com.minion.scaffold.core.navigation.UrlCreateRoute
import com.minion.scaffold.core.navigation.VCardCreateRoute
import com.minion.scaffold.core.navigation.WifiCreateRoute
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.qrscan.data.ImageDecodeResult
import com.minion.scaffold.core.emv.model.QrParseError
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.url.usecase.ParseUrlPayloadUseCase
import com.minion.scaffold.core.vcard.usecase.ParseVCardPayloadUseCase
import com.minion.scaffold.core.wifi.usecase.ParseWifiPayloadUseCase
import com.minion.scaffold.feature.qrscan.domain.DecodeScannedPayloadUseCase
import com.minion.scaffold.feature.qrscan.domain.ScannedContent
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `advanceUntilIdle` is what makes the "emits nothing" assertions mean anything: under
 * `StandardTestDispatcher` a queued coroutine has not run yet, so `expectNoEvents` would pass
 * against an implementation that does emit. Draining the scheduler first is the difference
 * between asserting absence and asserting "not yet".
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class QrScanViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val imageDecoder = FakeImageBarcodeDecoder()
    private val viewModel = viewModel()

    /**
     * The purpose reaches the ViewModel through its route, so the handle is seeded the way
     * navigation seeds it.
     *
     * The enum instance, **not** its name. Navigation decodes the argument before it reaches the
     * handle, and an earlier version of this helper stored a `String` — which matched a production
     * read of `get<String>` and let both agree with each other while disagreeing with the
     * framework. Every test passed; the screen threw `ClassCastException` on first launch.
     */
    private fun viewModel(purpose: ScanPurpose = ScanPurpose.Inspect) = QrScanViewModel(
        SavedStateHandle(mapOf(QrScanRoute.ARG_PURPOSE to purpose)),
        DecodeScannedPayloadUseCase(
            parseWifiPayload = ParseWifiPayloadUseCase(),
            parseVCardPayload = ParseVCardPayloadUseCase(),
            parseUrlPayload = ParseUrlPayloadUseCase(),
            parseEmvPayload = ParseEmvPayloadUseCase(),
        ),
        imageDecoder,
    )

    /** The decoder under test ignores it; it only has to be a non-null [Uri]. */
    private val imageUri = mockk<Uri>()

    @Test
    fun `starts idle with an empty input`() {
        assertEquals(QrScanState(), viewModel.state.value)
    }

    @Test
    fun `keystrokes update the input without decoding`() {
        viewModel.onIntent(QrScanIntent.ManualPayloadChanged("0002"))

        assertEquals("0002", viewModel.state.value.manualPayload)
        assertEquals(QrScanState.ContentState.Idle, viewModel.state.value.content)
    }

    @Test
    fun `a valid payload produces a verified report`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        val content = viewModel.state.value.content
        assertTrue(content is QrScanState.ContentState.Success)
        assertTrue((content as QrScanState.ContentState.Success).paymentReport().crc.passed)
    }

    @Test
    fun `a tampered payload still produces a report, with the checksum failing`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_TAMPERED))

        val content = viewModel.state.value.content
        assertTrue(content is QrScanState.ContentState.Success)
        assertFalse((content as QrScanState.ContentState.Success).paymentReport().crc.passed)
    }

    /** "Wrong kind of code" — not "this code is damaged", which is what [Parse] would say. */
    @Test
    fun `a code in no supported format is reported as unrecognised`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(UNRECOGNISED_PAYLOAD))

        assertEquals(
            QrScanState.ContentState.Failure(QrScanError.UnrecognisedFormat),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `a link is recognised as a web link`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(URL_PAYLOAD))

        val scanned = viewModel.state.value.scannedOrFail()
        assertTrue(scanned is ScannedContent.Web)
        assertEquals(URL_PAYLOAD, (scanned as ScannedContent.Web).url)
    }

    @Test
    fun `a contact card is recognised as a contact`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(VCARD_PAYLOAD))

        val scanned = viewModel.state.value.scannedOrFail()
        assertTrue(scanned is ScannedContent.Contact)
        assertEquals("Jane Smith", (scanned as ScannedContent.Contact).card.formattedName)
    }

    /** The report shows what a card carries, so the parser has to hand the extras over. */
    @Test
    fun `a contact card keeps the properties the form cannot show`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(VCARD_WITH_ADDRESS))

        val contact = viewModel.state.value.scannedOrFail() as ScannedContent.Contact
        assertEquals(listOf("ADR"), contact.card.passthrough.map { it.name })
    }

    @Test
    fun `opening a link emits the scanned address`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(URL_PAYLOAD))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.OpenLinkRequested)
            advanceUntilIdle()

            assertEquals(QrScanEffect.OpenLink(URL_PAYLOAD), awaitItem())
        }
    }

    /** The action is only reachable from a link report, so a payment code must not trigger it. */
    @Test
    fun `opening a link emits nothing for another format`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.OpenLinkRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `adding a contact emits the scanned card`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(VCARD_PAYLOAD))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.AddContactRequested)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is QrScanEffect.AddContact)
            assertEquals("Jane Smith", (effect as QrScanEffect.AddContact).card.formattedName)
        }
    }

    @Test
    fun `adding a contact emits nothing for another format`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(URL_PAYLOAD))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.AddContactRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `editing a link routes to the link editor`() = runTest {
        val editing = viewModel(ScanPurpose.Edit)

        editing.effect.test {
            editing.onIntent(QrScanIntent.PayloadSubmitted(URL_PAYLOAD))
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(UrlCreateRoute(URL_PAYLOAD)), awaitItem())
        }
    }

    @Test
    fun `editing a contact card routes to the contact editor`() = runTest {
        val editing = viewModel(ScanPurpose.Edit)

        editing.effect.test {
            editing.onIntent(QrScanIntent.PayloadSubmitted(VCARD_PAYLOAD))
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(VCardCreateRoute(VCARD_PAYLOAD)), awaitItem())
        }
    }

    /**
     * A broken payment code keeps its typed error. Collapsing this into "unrecognized" would undo
     * the reason the parser reports what it reports.
     */
    @Test
    fun `a damaged payment code keeps its parse error`() {
        // Frames as EMV — opens with tag 00 — but has no checksum segment.
        viewModel.onIntent(QrScanIntent.PayloadSubmitted("000201010212"))

        assertEquals(
            QrScanState.ContentState.Failure(QrScanError.Parse(QrParseError.MissingCrc)),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `a Wi-Fi code is recognised as Wi-Fi`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(WIFI_PAYLOAD))

        val content = viewModel.state.value.content
        assertTrue(content is QrScanState.ContentState.Success)
        val scanned = (content as QrScanState.ContentState.Success).content
        assertTrue(scanned is ScannedContent.Wifi)
        assertEquals("Guest", (scanned as ScannedContent.Wifi).credentials.ssid)
    }

    /** Editing a Wi-Fi code has to open the Wi-Fi form, not the payment one. */
    @Test
    fun `editing a Wi-Fi code routes to the Wi-Fi editor`() = runTest {
        val editing = viewModel(ScanPurpose.Edit)

        editing.effect.test {
            editing.onIntent(QrScanIntent.PayloadSubmitted(WIFI_PAYLOAD))
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(WifiCreateRoute(WIFI_PAYLOAD)), awaitItem())
        }
    }

    @Test
    fun `the report's edit action routes a Wi-Fi code to the Wi-Fi editor`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(WIFI_PAYLOAD))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.EditRequested)
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(WifiCreateRoute(WIFI_PAYLOAD)), awaitItem())
        }
    }

    /**
     * The guard that keeps a live camera from re-decoding its own success thirty times a second.
     */
    @Test
    fun `a second payload is ignored while a report is on screen`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(UNRECOGNISED_PAYLOAD))

        val content = viewModel.state.value.content
        assertTrue(content is QrScanState.ContentState.Success)
    }

    /** A failure is not a lock-out: the user has to be able to fix a typo and try again. */
    @Test
    fun `a second payload is accepted after a failure`() {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(UNRECOGNISED_PAYLOAD))
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        assertTrue(viewModel.state.value.content is QrScanState.ContentState.Success)
    }

    @Test
    fun `clearing returns to idle and empties the input`() {
        viewModel.onIntent(QrScanIntent.ManualPayloadChanged(ScanSamples.QRIS_DYNAMIC))
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.onIntent(QrScanIntent.Cleared)

        assertEquals(QrScanState(), viewModel.state.value)
    }

    @Test
    fun `copying emits the current report exactly once`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.CopyReportRequested)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is QrScanEffect.CopyReport)
            assertEquals(
                ScanSamples.QRIS_DYNAMIC,
                (effect as QrScanEffect.CopyReport).content.payload,
            )
            expectNoEvents()
        }
    }

    @Test
    fun `sharing emits the current report`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.ShareReportRequested)
            advanceUntilIdle()

            assertTrue(awaitItem() is QrScanEffect.ShareReport)
        }
    }

    /** Nothing to copy is not an error state — it is simply nothing happening. */
    @Test
    fun `copying with no report emits nothing`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.CopyReportRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    /**
     * The edit tool is this screen with a different destination. Scanning forwards the payload the
     * moment it decodes, without the user pressing anything.
     */
    @Test
    fun `scanning for an edit forwards the payload`() = runTest {
        val editing = viewModel(ScanPurpose.Edit)

        editing.effect.test {
            editing.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(QrCreateRoute(ScanSamples.QRIS_DYNAMIC)), awaitItem())
        }
    }

    /** Inspecting is the default, and it must not jump the user somewhere they did not ask to go. */
    @Test
    fun `scanning to inspect forwards nothing`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    /**
     * The report is still shown after forwarding, so coming back from the editor lands on
     * something rather than an empty scanner.
     */
    @Test
    fun `forwarding still leaves the report on screen`() {
        val editing = viewModel(ScanPurpose.Edit)

        editing.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        assertTrue(editing.state.value.content is QrScanState.ContentState.Success)
        assertFalse(editing.state.value.isScanning)
    }

    @Test
    fun `an unreadable payload forwards nothing even when editing`() = runTest {
        val editing = viewModel(ScanPurpose.Edit)

        editing.effect.test {
            editing.onIntent(QrScanIntent.PayloadSubmitted(UNRECOGNISED_PAYLOAD))
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `the report's edit action forwards the scanned payload`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.EditRequested)
            advanceUntilIdle()

            assertEquals(QrScanEffect.EditPayload(QrCreateRoute(ScanSamples.QRIS_DYNAMIC)), awaitItem())
        }
    }

    @Test
    fun `the edit action does nothing without a report`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.EditRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `a granted permission unlocks scanning`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )

        assertEquals(PermissionState.Granted, viewModel.state.value.cameraPermission)
        assertTrue(viewModel.state.value.isScanning)
    }

    /** A refusal the system will prompt for again is recoverable in-app. */
    @Test
    fun `a refusal that can be retried is Denied`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = false, shouldShowRationale = true),
        )

        assertEquals(PermissionState.Denied, viewModel.state.value.cameraPermission)
    }

    /**
     * No rationale after a refusal means the system will not show the dialog again, so the only
     * route back is Settings — a different button from the one [Denied] gets.
     */
    @Test
    fun `a refusal that cannot be retried is PermanentlyDenied`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = false, shouldShowRationale = false),
        )

        assertEquals(
            PermissionState.PermanentlyDenied,
            viewModel.state.value.cameraPermission,
        )
    }

    @Test
    fun `scanning stops once a report is on screen`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `scanning stops in manual mode even with the permission granted`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        viewModel.onIntent(QrScanIntent.ModeChanged(InputMode.Manual))

        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `the torch toggles`() {
        assertFalse(viewModel.state.value.torchEnabled)

        viewModel.onIntent(QrScanIntent.TorchToggled)
        assertTrue(viewModel.state.value.torchEnabled)

        viewModel.onIntent(QrScanIntent.TorchToggled)
        assertFalse(viewModel.state.value.torchEnabled)
    }

    /** Clearing reopens the camera without undoing the permission the user already granted. */
    @Test
    fun `clearing after a scan resumes scanning`() {
        viewModel.onIntent(
            QrScanIntent.PermissionResult(granted = true, shouldShowRationale = false),
        )
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.onIntent(QrScanIntent.Cleared)

        assertTrue(viewModel.state.value.isScanning)
        assertEquals(PermissionState.Granted, viewModel.state.value.cameraPermission)
    }

    @Test
    fun `requesting settings emits the effect`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.AppSettingsRequested)
            advanceUntilIdle()

            assertEquals(QrScanEffect.OpenAppSettings, awaitItem())
        }
    }

    @Test
    fun `picking an image shows progress until the decode finishes`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))

        assertEquals(QrScanState.ContentState.Decoding, viewModel.state.value.content)

        imageDecoder.complete(ImageDecodeResult.Found(ScanSamples.QRIS_DYNAMIC))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.content is QrScanState.ContentState.Success)
    }

    /**
     * The two stages of the gallery path fail differently, and the screen says different things
     * about them: this image holds no QR, versus this QR is not a valid EMV payload.
     */
    @Test
    fun `an image with no QR code reports that, not a parse failure`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.NoBarcode)
        advanceUntilIdle()

        assertEquals(
            QrScanState.ContentState.Failure(QrScanError.NoBarcodeInImage),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `an unopenable image reports that`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.Unreadable)
        advanceUntilIdle()

        assertEquals(
            QrScanState.ContentState.Failure(QrScanError.ImageUnreadable),
            viewModel.state.value.content,
        )
    }

    @Test
    fun `an image holding an unsupported QR reports an unrecognised format`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.Found(UNRECOGNISED_PAYLOAD))
        advanceUntilIdle()

        assertEquals(
            QrScanState.ContentState.Failure(QrScanError.UnrecognisedFormat),
            viewModel.state.value.content,
        )
    }

    /** The gallery path shares one decoder with the camera, so it recognizes the same formats. */
    @Test
    fun `an image holding a Wi-Fi QR is recognised as Wi-Fi`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.Found(WIFI_PAYLOAD))
        advanceUntilIdle()

        val content = viewModel.state.value.content as QrScanState.ContentState.Success
        assertTrue(content.content is ScannedContent.Wifi)
    }

    /** Double-tapping the picker must not start a second decode over the first. */
    @Test
    fun `a second image is ignored while the first is still decoding`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        advanceUntilIdle()

        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        advanceUntilIdle()

        assertEquals(1, imageDecoder.callCount)

        imageDecoder.complete(ImageDecodeResult.NoBarcode)
        advanceUntilIdle()
    }

    @Test
    fun `a camera payload is ignored while an image is decoding`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))

        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        assertEquals(QrScanState.ContentState.Decoding, viewModel.state.value.content)

        imageDecoder.complete(ImageDecodeResult.NoBarcode)
        advanceUntilIdle()
    }

    @Test
    fun `an image is ignored while a report is on screen`() = runTest {
        viewModel.onIntent(QrScanIntent.PayloadSubmitted(ScanSamples.QRIS_DYNAMIC))

        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        advanceUntilIdle()

        assertEquals(0, imageDecoder.callCount)
    }

    @Test
    fun `copying a single value emits it verbatim`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.CopyValueRequested("936000220000000282"))
            advanceUntilIdle()

            assertEquals(QrScanEffect.CopyText("936000220000000282"), awaitItem())
        }
    }

    /** Unlike the whole-report copy, this needs no report — a value the screen rendered is enough. */
    @Test
    fun `copying a single value works with no report on screen`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanIntent.CopyValueRequested("ID"))
            advanceUntilIdle()

            assertTrue(awaitItem() is QrScanEffect.CopyText)
        }
    }

    /**
     * Back dismisses an in-flight image decode. Without a guard to decode would finish afterward
     * and reopen the report the user just closed.
     */
    @Test
    fun `a decode dismissed before it finishes does not reopen`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        advanceUntilIdle()

        viewModel.onIntent(QrScanIntent.Cleared)
        imageDecoder.complete(ImageDecodeResult.Found(ScanSamples.QRIS_DYNAMIC))
        advanceUntilIdle()

        assertEquals(QrScanState.ContentState.Idle, viewModel.state.value.content)
    }

    /** A failed image is not a lock-out either. */
    @Test
    fun `another image is accepted after one fails`() = runTest {
        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.NoBarcode)
        advanceUntilIdle()

        viewModel.onIntent(QrScanIntent.ImagePicked(imageUri))
        imageDecoder.complete(ImageDecodeResult.Found(ScanSamples.QRIS_DYNAMIC))
        advanceUntilIdle()

        assertEquals(2, imageDecoder.callCount)
        assertTrue(viewModel.state.value.content is QrScanState.ContentState.Success)
    }

    /** A scanned payment code's report, failing loudly if something else was scanned. */
    private fun QrScanState.ContentState.Success.paymentReport(): QrInquiryReport =
        (content as ScannedContent.Payment).report

    /** Whatever was scanned, failing loudly when nothing was. */
    private fun QrScanState.scannedOrFail(): ScannedContent =
        (content as QrScanState.ContentState.Success).content

    private companion object {
        const val WIFI_PAYLOAD = "WIFI:T:WPA;S:Guest;P:hunter2!;;"
        const val URL_PAYLOAD = "https://example.com/menu"

        /**
         * Text no format claims.
         *
         * A URL used to serve this purpose and no longer can — one of these tests
         * passed for the wrong reason once a link became a recognised format: its
         * "failure" payload succeeded, and the guard it meant to test was never reached.
         */
        const val UNRECOGNISED_PAYLOAD = "just some text"

        /**
         * Raw strings with real line breaks, so these carry bare LF rather than CRLF.
         *
         * Which is a fair test in itself: plenty of generators emit LF and the parser accepts
         * either. The codec's own tests cover the canonical CRLF form.
         */
        val VCARD_PAYLOAD = """
            BEGIN:VCARD
            VERSION:3.0
            N:Smith;Jane;;;
            FN:Jane Smith
            END:VCARD
        """.trimIndent()

        val VCARD_WITH_ADDRESS = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Jane Smith
            ADR;TYPE=HOME:;;1 Long Road;Bekasi;;17151;ID
            END:VCARD
        """.trimIndent()
    }
}
