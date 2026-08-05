package com.minion.scaffold.feature.ocr.presentation.settings

import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.feature.ocr.domain.ObserveOcrEngineUseCase
import com.minion.scaffold.feature.ocr.domain.SetOcrEngineUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class OcrSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val setOcrEngine = mockk<SetOcrEngineUseCase>(relaxed = true)
    private val observeOcrEngine = mockk<ObserveOcrEngineUseCase>()
    private val engine = MutableStateFlow(OcrEngine.MlKit)

    private fun viewModel(): OcrSettingsViewModel {
        every { observeOcrEngine() } returns engine
        return OcrSettingsViewModel(observeOcrEngine, setOcrEngine)
    }

    @Test
    fun `defaults to ML Kit`() {
        assertEquals(OcrEngine.MlKit, viewModel().state.value.engine)
    }

    @Test
    fun `renders whatever the preference reports`() = runTest {
        val viewModel = viewModel()
        engine.value = OcrEngine.PaddleOcr
        advanceUntilIdle()

        assertEquals(OcrEngine.PaddleOcr, viewModel.state.value.engine)
    }

    @Test
    fun `selecting an engine writes it`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(OcrSettingsIntent.EngineSelected(OcrEngine.PaddleOcr))
        advanceUntilIdle()

        coVerify(exactly = 1) { setOcrEngine(OcrEngine.PaddleOcr) }
    }

    @Test
    fun `state follows the store, not the tap`() = runTest {
        val viewModel = viewModel()

        // The write is stubbed out and never feeds back into the flow, so the state must not move.
        // This is what stops the radio group showing an engine that failed to save.
        viewModel.onIntent(OcrSettingsIntent.EngineSelected(OcrEngine.PaddleOcr))
        advanceUntilIdle()

        assertEquals(OcrEngine.MlKit, viewModel.state.value.engine)
    }
}
