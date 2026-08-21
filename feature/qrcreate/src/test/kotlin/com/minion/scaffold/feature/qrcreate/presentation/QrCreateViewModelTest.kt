package com.minion.scaffold.feature.qrcreate.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.minion.scaffold.core.emv.model.EmvField
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.ViolationReason
import com.minion.scaffold.core.emv.reference.MerchantCategoryCodes
import com.minion.scaffold.core.emv.usecase.BuildEmvPayloadUseCase
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.EmvPayloadBreakdownUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.navigation.QrCreateRoute
import com.minion.scaffold.feature.qrcreate.presentation.preview.ExportOutcome
import com.minion.scaffold.feature.qrcreate.presentation.preview.QrExportEffect
import com.minion.scaffold.core.emv.model.EmvParseResult
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.qrcreate.presentation.form.EmvFormState
import com.minion.scaffold.feature.qrcreate.presentation.form.TipMode
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QrCreateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val exporter = FakeQrImageExporter()
    private val viewModel = viewModel()

    /**
     * [payload] is the route argument that turns this screen into the editor.
     *
     * A `String?` argument really is stored as a `String?`, unlike the scanner's enum — but the
     * key comes from the route rather than a literal, so the two cannot drift apart.
     */
    private fun viewModel(payload: String? = null) = QrCreateViewModel(
        SavedStateHandle(mapOf(QrCreateRoute.ARG_PAYLOAD to payload)),
        EmvDraftFromPayloadUseCase(ParseEmvPayloadUseCase()),
        BuildEmvPayloadUseCase(),
        EmvPayloadBreakdownUseCase(),
        exporter,
    )

    private val imageUri = mockk<Uri>()

    @Test
    fun `nothing is generated until the button is pressed`() {
        fillValidForm()

        assertNull(viewModel.state.value.payload)
        assertTrue(viewModel.state.value.violations.isEmpty())
    }

    @Test
    fun `generating a valid form produces a payload`() {
        fillValidForm()

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        assertNotNull(viewModel.state.value.payload)
        assertTrue(viewModel.state.value.violations.isEmpty())
    }

    /**
     * The round trip the two tools exist for: what this screen writes, the scan screen reads.
     */
    @Test
    fun `the generated payload parses back with a passing checksum`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        val result = ParseEmvPayloadUseCase()(viewModel.state.value.payload.orEmpty())

        assertTrue(result is EmvParseResult.Success)
        val report = (result as EmvParseResult.Success).value
        assertTrue(report.crc.passed)
        assertEquals(
            "PAK BOS QR 1",
            report.segments.single { it.node.tag == "59" }.node.rawValue,
        )
    }

    @Test
    fun `generating an empty form reports every missing field and no payload`() {
        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        val state = viewModel.state.value
        assertNull(state.payload)
        assertEquals(ViolationReason.REQUIRED, state.reasonFor(EmvField.MERCHANT_NAME))
        assertEquals(ViolationReason.REQUIRED, state.reasonFor(EmvField.MERCHANT_CITY))
        assertEquals(ViolationReason.REQUIRED, state.reasonFor(EmvField.MERCHANT_CATEGORY_CODE))
        assertEquals(ViolationReason.REQUIRED, state.reasonFor(EmvField.TRANSACTION_AMOUNT))
        assertEquals(
            ViolationReason.REQUIRED,
            state.reasonFor(EmvField.ACQUIRER_IDENTIFIER, accountIndex = 0),
        )
    }

    /** An error under a field the user is actively fixing is noise. */
    @Test
    fun `editing a field clears only that field's violation`() {
        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_NAME, "PAK BOS"))

        val state = viewModel.state.value
        assertNull(state.reasonFor(EmvField.MERCHANT_NAME))
        assertEquals(ViolationReason.REQUIRED, state.reasonFor(EmvField.MERCHANT_CITY))
    }

    /**
     * A QR beside fields it no longer matches is the one genuinely dangerous state here — it would
     * encode different values from the ones being read. The payload is kept so the user can see
     * that a result exists and needs regenerating, and marked stale so nothing can act on it.
     */
    @Test
    fun `editing after generating marks the payload stale rather than discarding it`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
        assertNotNull(viewModel.state.value.payload)
        assertFalse(viewModel.state.value.payloadStale)

        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, "Depok"))

        assertNotNull(viewModel.state.value.payload)
        assertTrue(viewModel.state.value.payloadStale)
        assertFalse(viewModel.state.value.hasUsablePayload)
    }

    /** Regenerating is what makes the payload current again. */
    @Test
    fun `generating again clears the stale mark`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, "Depok"))
        assertTrue(viewModel.state.value.payloadStale)

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        assertFalse(viewModel.state.value.payloadStale)
        assertTrue(viewModel.state.value.hasUsablePayload)
    }

    /**
     * The guard that makes keeping a stale payload on screen safe: it may be looked at, never
     * exported. Copying one would put the previous form's code on the clipboard.
     */
    @Test
    fun `a stale payload cannot be copied`() = runTest {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, "Depok"))

        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.CopyPayloadRequested)
            expectNoEvents()
        }
    }

    /** Clearing the form empties every field, and keeps the screen's purpose. */
    @Test
    fun `confirming a reset empties the form`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        viewModel.onIntent(QrCreateIntent.ResetRequested)
        assertTrue(viewModel.state.value.confirmingReset)
        viewModel.onIntent(QrCreateIntent.ResetConfirmed)

        val state = viewModel.state.value
        assertEquals("", state.form.merchantName)
        assertNull(state.payload)
        assertFalse(state.confirmingReset)
    }

    /** Backing out of the confirmation changes nothing. */
    @Test
    fun `dismissing a reset leaves the form alone`() {
        fillValidForm()
        val before = viewModel.state.value.form

        viewModel.onIntent(QrCreateIntent.ResetRequested)
        viewModel.onIntent(QrCreateIntent.ResetDismissed)

        assertEquals(before, viewModel.state.value.form)
        assertFalse(viewModel.state.value.confirmingReset)
    }

    /** The highlight breakdown appears with the payload, and every top-level tag it covers. */
    @Test
    fun `generating a valid form breaks the payload into tags`() {
        fillValidForm()

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        val tags = viewModel.state.value.tags
        assertTrue(tags.isNotEmpty())
        assertEquals("00", tags.first().path)
        assertTrue(tags.any { it.path == "63" })
    }

    /**
     * The breakdown is kept with the payload it describes. Both go stale together, and the screen
     * shows them behind the same scrim — a breakdown of the code actually on display is correct,
     * where an empty one beside a visible QR would not be.
     */
    @Test
    fun `editing after generating keeps the tag breakdown with its payload`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
        val generated = viewModel.state.value.tags
        assertTrue(generated.isNotEmpty())

        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, "Depok"))

        assertEquals(generated, viewModel.state.value.tags)
    }

    @Test
    fun `switching to static clears the amount`() {
        fillValidForm()

        viewModel.onIntent(
            QrCreateIntent.InitiationMethodChanged(PointOfInitiationMethod.STATIC),
        )

        assertEquals("", viewModel.state.value.form.amount)
    }

    @Test
    fun `a static payload generates without an amount`() {
        fillValidForm()
        viewModel.onIntent(
            QrCreateIntent.InitiationMethodChanged(PointOfInitiationMethod.STATIC),
        )

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        assertNotNull(viewModel.state.value.payload)
    }

    @Test
    fun `adding and removing the national switch template`() {
        viewModel.onIntent(QrCreateIntent.AccountAdded)
        assertEquals(2, viewModel.state.value.form.accounts.size)
        assertEquals("51", viewModel.state.value.form.accounts[1].tag)

        viewModel.onIntent(QrCreateIntent.AccountRemoved(1))
        assertEquals(1, viewModel.state.value.form.accounts.size)
    }

    @Test
    fun `copying emits the generated payload`() = runTest {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
        val payload = viewModel.state.value.payload

        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.CopyPayloadRequested)
            advanceUntilIdle()

            assertEquals(QrExportEffect.CopyText(payload.orEmpty()), awaitItem())
        }
    }

    @Test
    fun `copying with nothing generated emits nothing`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.CopyPayloadRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `sharing emits the uri the exporter produced`() = runTest {
        generated()

        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.ShareImageRequested)
            exporter.completeShare(imageUri)
            advanceUntilIdle()

            assertEquals(QrExportEffect.ShareImage(imageUri), awaitItem())
        }
    }

    @Test
    fun `a share that cannot be written reports a failure`() = runTest {
        generated()

        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.ShareImageRequested)
            exporter.completeShare(null)
            advanceUntilIdle()

            assertEquals(
                QrExportEffect.ShowExportMessage(ExportOutcome.EXPORT_FAILED),
                awaitItem(),
            )
        }
    }

    /** Saving lands in another app entirely, so without the message the button reads as broken. */
    @Test
    fun `saving to the gallery reports success`() = runTest {
        generated()

        viewModel.effect.test {
            viewModel.onIntent(QrCreateIntent.SaveImageRequested)
            exporter.completeSave(true)
            advanceUntilIdle()

            assertEquals(
                QrExportEffect.ShowExportMessage(ExportOutcome.SAVED_TO_GALLERY),
                awaitItem(),
            )
        }
    }

    @Test
    fun `a second export is ignored while the first is running`() = runTest {
        generated()

        viewModel.onIntent(QrCreateIntent.SaveImageRequested)
        advanceUntilIdle()
        viewModel.onIntent(QrCreateIntent.SaveImageRequested)
        advanceUntilIdle()

        assertEquals(1, exporter.saveCallCount)

        exporter.completeSave(true)
        advanceUntilIdle()
    }

    @Test
    fun `exporting with nothing generated does not reach the exporter`() = runTest {
        viewModel.onIntent(QrCreateIntent.ShareImageRequested)
        advanceUntilIdle()

        assertEquals(0, exporter.shareCallCount)
    }

    @Test
    fun `the exporter receives the generated payload`() = runTest {
        generated()

        viewModel.onIntent(QrCreateIntent.SaveImageRequested)
        exporter.completeSave(true)
        advanceUntilIdle()

        assertEquals(viewModel.state.value.payload, exporter.lastPayload)
    }

    @Test
    fun `a tip prompt reaches the payload`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.PROMPT))

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        val payload = viewModel.state.value.payload
        assertNotNull(payload)
        assertTrue(payload.orEmpty().contains("55020"))
    }

    @Test
    fun `a fee mode with no value blocks generation`() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.FIXED_FEE))

        viewModel.onIntent(QrCreateIntent.GenerateRequested)

        assertNull(viewModel.state.value.payload)
        assertEquals(
            ViolationReason.REQUIRED,
            viewModel.state.value.reasonFor(EmvField.CONVENIENCE_FEE),
        )
    }

    /**
     * A fixed fee of 15000 left behind when switching to a percentage would read as 15000%, and be
     * rejected for a reason that has nothing to do with what the user just did.
     */
    @Test
    fun `changing tip mode clears the value`() {
        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.FIXED_FEE))
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.CONVENIENCE_FEE, "15000"))

        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.PERCENTAGE_FEE))

        assertEquals("", viewModel.state.value.form.tipValue)
    }

    @Test
    fun `selecting no tip clears a value that was already entered`() {
        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.PERCENTAGE_FEE))
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.CONVENIENCE_FEE, "5"))

        viewModel.onIntent(QrCreateIntent.TipModeChanged(TipMode.NONE))

        assertEquals("", viewModel.state.value.form.tipValue)
    }

    @Test
    fun `a payload on the route fills the form`() {
        val editor = viewModel(payload = SAMPLE_PAYLOAD)

        val form = editor.state.value.form
        assertTrue(editor.state.value.editing)
        assertEquals("PAK BOS QR 1", form.merchantName)
        assertEquals("Bekasi", form.merchantCity)
        assertEquals("17151", form.postalCode)
        assertEquals("15000000.00", form.amount)
        assertEquals("IDR", form.currency?.alphaCode)
        assertEquals(2, form.accounts.size)
        assertEquals("ID.CO.CIMBNIAGA.WWW", form.accounts[0].identifier)
        assertEquals("936000220000000282", form.accounts[0].merchantPan)
        assertEquals("000008160012605", form.accounts[0].merchantId)
    }

    /**
     * The whole point of the edit tool: change one field and everything else comes back untouched,
     * including the tags the form has no box for.
     */
    @Test
    fun `regenerating after one edit preserves the rest of the payload`() {
        val editor = viewModel(payload = SAMPLE_PAYLOAD)

        editor.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_NAME, "PAK BOS QR 2"))
        editor.onIntent(QrCreateIntent.GenerateRequested)

        val regenerated = editor.state.value.payload.orEmpty()
        assertEquals(
            SAMPLE_PAYLOAD.dropLast(CRC_LENGTH).replace("PAK BOS QR 1", "PAK BOS QR 2"),
            regenerated.dropLast(CRC_LENGTH),
        )
    }

    /** A null edit has to be a no-op, or the tool corrupts codes it was only asked to open. */
    @Test
    fun `generating without changing anything reproduces the payload exactly`() {
        val editor = viewModel(payload = SAMPLE_PAYLOAD)

        editor.onIntent(QrCreateIntent.GenerateRequested)

        assertEquals(SAMPLE_PAYLOAD, editor.state.value.payload)
    }

    @Test
    fun `an empty route argument leaves a blank form in create mode`() {
        assertEquals(QrCreateState(), viewModel.state.value)
    }

    @Test
    fun `an unreadable route argument says so instead of failing silently`() {
        val editor = viewModel(payload = "https://example.com")

        assertTrue(editor.state.value.prefillFailed)
        assertTrue(editor.state.value.editing)
        assertEquals(EmvFormState(), editor.state.value.form)
    }

    private fun generated() {
        fillValidForm()
        viewModel.onIntent(QrCreateIntent.GenerateRequested)
    }

    /** The smallest set of edits that satisfies the builder. Currency and country default. */
    private fun fillValidForm() {
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_NAME, "PAK BOS QR 1"))
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.MERCHANT_CITY, "Bekasi"))
        viewModel.onIntent(QrCreateIntent.FieldChanged(EmvField.TRANSACTION_AMOUNT, "15000000.00"))
        viewModel.onIntent(QrCreateIntent.CategorySelected(MerchantCategoryCodes.all.first()))
        viewModel.onIntent(
            QrCreateIntent.AccountFieldChanged(
                index = 0,
                field = EmvField.ACQUIRER_IDENTIFIER,
                value = "ID.CO.CIMBNIAGA.WWW",
            ),
        )
    }

    private companion object {
        const val CRC_LENGTH = 4

        /** A live QRIS payload, including a flat tag `04` and subtag `02` on both templates. */
        const val SAMPLE_PAYLOAD =
            "000201010212041553919900000019026710019ID.CO.CIMBNIAGA.WWW0118936000220000000282021" +
                "50000081600126050303UMI51450015ID.OR.QRNPG.WWW0215ID00000000001230303UMI520407805" +
                "303360541115000000.005802ID5912PAK BOS QR 16006Bekasi61051715163043D58"
    }
}
