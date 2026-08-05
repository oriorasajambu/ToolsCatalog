package com.minion.scaffold.feature.texttools.presentation.transform

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.text.model.TextError
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.text.usecase.TransformTextUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class TextToolsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = viewModel()

    /**
     * @param seededText what another tool handed over, as navigation would have put it in the
     *   handle. Read by name rather than through `toRoute()`, which needs an Android `Bundle`
     *   that does not exist here — see `QrScanRoute.ARG_PURPOSE`.
     */
    private fun viewModel(seededText: String? = null) = TextToolsViewModel(
        savedStateHandle = SavedStateHandle(
            seededText?.let { mapOf(TextToolsRoute.ARG_TEXT to it) }.orEmpty(),
        ),
        transformText = TransformTextUseCase(),
    )

    @Test
    fun `starts empty when nothing was handed over`() {
        assertEquals("", viewModel.state.value.input)
    }

    @Test
    fun `text handed over by another tool pre-fills the input and is transformed`() {
        val seeded = viewModel(seededText = "Man")
        seeded.onIntent(TextToolsIntent.OperationChanged(TextOperation.BASE64_ENCODE))

        assertEquals("Man", seeded.state.value.input)
        assertEquals("TWFu", seeded.state.value.output)
    }

    @Test
    fun `an empty handover is ignored rather than seeding a blank input`() {
        assertEquals("", viewModel(seededText = "").state.value.input)
    }

    @Test
    fun `output recomputes as the input changes`() {
        viewModel.onIntent(TextToolsIntent.OperationChanged(TextOperation.BASE64_ENCODE))
        viewModel.onIntent(TextToolsIntent.InputChanged("Man"))

        assertEquals("TWFu", viewModel.state.value.output)
    }

    @Test
    fun `output recomputes as the operation changes`() {
        viewModel.onIntent(TextToolsIntent.InputChanged("Man"))
        viewModel.onIntent(TextToolsIntent.OperationChanged(TextOperation.HEX_ENCODE))

        assertEquals("4d616e", viewModel.state.value.output)
    }

    /** A failed decode clears the stale output rather than leaving it under a red field. */
    @Test
    fun `a decode failure clears the output and shows the error`() {
        viewModel.onIntent(TextToolsIntent.OperationChanged(TextOperation.BASE64_DECODE))
        viewModel.onIntent(TextToolsIntent.InputChanged("not base64!!"))

        assertEquals("", viewModel.state.value.output)
        assertEquals(TextError.NOT_VALID_BASE64, viewModel.state.value.error)
    }

    @Test
    fun `fixing the input clears the error`() {
        viewModel.onIntent(TextToolsIntent.OperationChanged(TextOperation.BASE64_DECODE))
        viewModel.onIntent(TextToolsIntent.InputChanged("bad!"))
        viewModel.onIntent(TextToolsIntent.InputChanged("TWFu"))

        assertNull(viewModel.state.value.error)
        assertEquals("Man", viewModel.state.value.output)
    }

    @Test
    fun `copying emits the current output`() = runTest {
        viewModel.onIntent(TextToolsIntent.InputChanged("Man"))

        viewModel.effect.test {
            viewModel.onIntent(TextToolsIntent.CopyOutputRequested)
            advanceUntilIdle()

            assertEquals(TextToolsEffect.CopyText("TWFu"), awaitItem())
        }
    }

    @Test
    fun `copying an empty output emits nothing`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(TextToolsIntent.CopyOutputRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }
}
