package com.minion.scaffold.feature.qrscan.presentation.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.minion.scaffold.core.emv.usecase.EmvDraftFromPayloadUseCase
import com.minion.scaffold.core.emv.usecase.ParseEmvPayloadUseCase
import com.minion.scaffold.core.navigation.QrScanSettingsRoute
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.qrscan.data.SchemaDocument
import com.minion.scaffold.feature.qrscan.data.SchemaDocumentReader
import com.minion.scaffold.feature.qrscan.domain.export.FakePaymentSchemaRepository
import com.minion.scaffold.feature.qrscan.domain.export.PaymentSchemaSource
import com.minion.scaffold.feature.qrscan.domain.export.ResolvePlaceholdersUseCase
import com.minion.scaffold.feature.qrscan.domain.export.ValidateSchemaUseCase
import com.minion.scaffold.feature.qrscan.presentation.ScanSamples
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class QrScanSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val schemaRepository = FakePaymentSchemaRepository()
    private val documentReader = FakeSchemaDocumentReader()

    /** Built on first use — see the note in `QrScanViewModelTest`. */
    private val viewModel by lazy { viewModel() }

    private fun viewModel(payload: String? = null) = QrScanSettingsViewModel(
        SavedStateHandle(mapOf(QrScanSettingsRoute.ARG_PAYLOAD to payload)),
        schemaRepository,
        ValidateSchemaUseCase(),
        documentReader,
        ResolvePlaceholdersUseCase(EmvDraftFromPayloadUseCase(ParseEmvPayloadUseCase())),
        ParseEmvPayloadUseCase(),
    )

    /** The picker returns one; nothing here reads it. */
    private val uri = mockk<Uri>()

    @Test
    fun `starts on the built-in schema`() = runTest {
        advanceUntilIdle()

        assertEquals(PaymentSchemaSource.BuiltIn, viewModel.state.value.source)
        assertFalse(viewModel.state.value.canReset)
    }

    @Test
    fun `a valid template becomes the active schema`() = runTest {
        documentReader.result = SchemaDocument.Read(
            text = """{ "name": "{{merchant_name}}" }""",
            label = "acquirer-v2.json",
        )

        viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(uri))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(PaymentSchemaSource.Custom, state.source)
        assertEquals("acquirer-v2.json", state.label)
        assertTrue(state.canReset)
        assertNull(state.importError)
    }

    @Test
    fun `a template that is not JSON is refused and nothing is stored`() = runTest {
        documentReader.result = SchemaDocument.Read(text = "not json", label = "x.json")

        viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(uri))
        advanceUntilIdle()

        assertEquals(SchemaImportError.NotJson, viewModel.state.value.importError)
        // The point of validating at import: the schema in use is untouched.
        assertEquals(PaymentSchemaSource.BuiltIn, viewModel.state.value.source)
    }

    @Test
    fun `a template naming something unknown is refused, and says what`() = runTest {
        documentReader.result = SchemaDocument.Read(
            text = """{ "a": "{{merchant_nmae}}" }""",
            label = "x.json",
        )

        viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(uri))
        advanceUntilIdle()

        assertEquals(
            SchemaImportError.UnknownPlaceholders(listOf("merchant_nmae")),
            viewModel.state.value.importError,
        )
        assertEquals(PaymentSchemaSource.BuiltIn, viewModel.state.value.source)
    }

    @Test
    fun `an unreadable file is refused`() = runTest {
        documentReader.result = SchemaDocument.Unreadable

        viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(uri))
        advanceUntilIdle()

        assertEquals(SchemaImportError.Unreadable, viewModel.state.value.importError)
    }

    @Test
    fun `resetting returns to the built-in`() = runTest {
        documentReader.result = SchemaDocument.Read(text = "{}", label = "x.json")
        viewModel.onIntent(QrScanSettingsIntent.SchemaPicked(uri))
        advanceUntilIdle()

        viewModel.onIntent(QrScanSettingsIntent.ResetRequested)
        advanceUntilIdle()

        assertEquals(PaymentSchemaSource.BuiltIn, viewModel.state.value.source)
    }

    @Test
    fun `exporting hands over the active template`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(QrScanSettingsIntent.ExportRequested)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is QrScanSettingsEffect.ShareSchema)
            assertEquals(
                FakePaymentSchemaRepository.builtInAsset,
                (effect as QrScanSettingsEffect.ShareSchema).text,
            )
        }
    }

    @Test
    fun `without a payload the reference describes but does not resolve`() = runTest {
        advanceUntilIdle()

        val rows = viewModel.state.value.placeholders
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.value == null })
        assertTrue(rows.any { it.token == "merchant_pan" })
    }

    @Test
    fun `with a payload the reference carries that code's own values`() = runTest {
        val screen = viewModel(payload = ScanSamples.QRIS_DYNAMIC)
        advanceUntilIdle()

        val rows = screen.state.value.placeholders
        assertEquals(
            "936000220000000282",
            rows.first { it.token == "merchant_pan" }.value,
        )
        // Empty rather than null: this code carries nothing there, which is a different statement
        // from not having been asked.
        assertEquals("", rows.first { it.token == "tips" }.value)
    }

    /** A reader whose answer the test sets. */
    private class FakeSchemaDocumentReader : SchemaDocumentReader {

        var result: SchemaDocument = SchemaDocument.Unreadable

        override suspend fun read(uri: Uri): SchemaDocument = result
    }
}
