package com.minion.scaffold.feature.ocr.presentation

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.minion.scaffold.core.camera.CapturedFrame
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.ocr.model.OcrEngine
import com.minion.scaffold.core.ocr.usecase.AssembleTextUseCase
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.ocr.data.ImageLoader
import com.minion.scaffold.feature.ocr.data.OcrResult
import com.minion.scaffold.feature.ocr.domain.ObserveOcrEngineUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class OcrViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val recognizer = FakeTextRecognizer()
    private val imageLoader = mockk<ImageLoader>()

    /** Drives the engine preference, so a test can switch engines mid-session. */
    private val engine = MutableStateFlow(OcrEngine.MlKit)
    private val observeOcrEngine = mockk<ObserveOcrEngineUseCase>().also {
        every { it() } returns engine
    }

    /** The decoder is mocked out entirely, so the bitmap only has to be a non-null instance. */
    private val bitmap = mockk<Bitmap>(relaxed = true)

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = OcrViewModel(
        savedStateHandle = savedState,
        imageLoader = imageLoader,
        textRecognizer = recognizer,
        assembleText = AssembleTextUseCase(),
        observeOcrEngine = observeOcrEngine,
    )

    private fun frame() = CapturedFrame(jpegBytes = ByteArray(0), rotationDegrees = 0)

    private fun stubLoad() {
        coEvery { imageLoader.load(any<ByteArray>(), any()) } returns bitmap
        every { imageLoader.rotateQuarterTurn(any()) } returns bitmap
    }

    @Test
    fun `starts at capture with permission unknown`() {
        val viewModel = viewModel()

        assertEquals(PermissionState.Unknown, viewModel.state.value.permission)
        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
    }

    @Test
    fun `a denied result that can still be prompted becomes Denied`() {
        val viewModel = viewModel()
        viewModel.onIntent(OcrIntent.PermissionResult(granted = false, shouldShowRationale = true))

        assertEquals(PermissionState.Denied, viewModel.state.value.permission)
    }

    @Test
    fun `a denied result that cannot be prompted becomes PermanentlyDenied`() {
        val viewModel = viewModel()
        viewModel.onIntent(OcrIntent.PermissionResult(granted = false, shouldShowRationale = false))

        assertEquals(PermissionState.PermanentlyDenied, viewModel.state.value.permission)
    }

    @Test
    fun `a capture with text moves to selection with everything selected`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("first", "second"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        val capture = viewModel.state.value.currentCapture
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
        assertEquals(setOf("0", "1"), capture?.selectedBlockIds)
    }

    @Test
    fun `a capture with no text still becomes a capture, so it can be rotated`() = runTest {
        stubLoad()
        recognizer.enqueue(OcrResult.NoText)

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        // Re-shooting would be the wrong ask for a sideways photo — the bitmap is kept so
        // rotate-and-retry has something to work with.
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
        assertEquals(OcrNotice.NoTextFound, viewModel.state.value.notice)
    }

    @Test
    fun `an undecodable image reports unreadable and stays put`() = runTest {
        coEvery { imageLoader.load(any<ByteArray>(), any()) } returns null

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
        assertEquals(OcrNotice.ImageUnreadable, viewModel.state.value.notice)
    }

    @Test
    fun `a failed capture reports it without leaving the viewfinder`() {
        val viewModel = viewModel()
        viewModel.onFrameCaptured(null)

        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
        assertEquals(OcrNotice.CaptureFailed, viewModel.state.value.notice)
    }

    @Test
    fun `toggling a block removes it from the selection, and again restores it`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("keep", "drop"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.BlockToggled("1"))
        assertEquals(setOf("0"), viewModel.state.value.currentCapture?.selectedBlockIds)

        viewModel.onIntent(OcrIntent.BlockToggled("1"))
        assertEquals(setOf("0", "1"), viewModel.state.value.currentCapture?.selectedBlockIds)
    }

    @Test
    fun `select-all toggles the whole selection off and back on`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("a", "b"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.SelectAllToggled)
        assertEquals(emptySet<String>(), viewModel.state.value.currentCapture?.selectedBlockIds)

        viewModel.onIntent(OcrIntent.SelectAllToggled)
        assertEquals(setOf("0", "1"), viewModel.state.value.currentCapture?.selectedBlockIds)
    }

    @Test
    fun `confirming assembles only the selected blocks`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("keep", "drop"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.BlockToggled("1"))
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        assertEquals(OcrState.Stage.Result, viewModel.state.value.stage)
        assertEquals("keep", viewModel.state.value.editedText)
    }

    @Test
    fun `rotate and retry re-recognises and replaces the capture`() = runTest {
        stubLoad()
        recognizer.enqueue(OcrResult.NoText, FakeTextRecognizer.found("now readable"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.RotateAndRetry)
        advanceUntilIdle()

        assertEquals(2, recognizer.callCount)
        assertEquals(
            listOf("now readable"),
            viewModel.state.value.currentCapture?.text?.blocks?.map { it.text },
        )
    }

    @Test
    fun `appending a page keeps the first capture and returns to the viewfinder`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("page one"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.AddAnotherPage)
        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
        assertEquals(1, viewModel.state.value.captures.size)

        recognizer.enqueue(FakeTextRecognizer.found("page two"))
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        // Blank line between pages, so the seam is visible in the output.
        assertEquals("page one\n\npage two", viewModel.state.value.editedText)
        assertEquals(2, viewModel.state.value.captures.size)
    }

    @Test
    fun `removing the only capture returns to the viewfinder`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("only"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        val captureId = viewModel.state.value.captures.single().id
        viewModel.onIntent(OcrIntent.CaptureRemoved(captureId))

        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
        assertEquals("", viewModel.state.value.editedText)
    }

    @Test
    fun `editing the result replaces the assembled text`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("typo"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        viewModel.onIntent(OcrIntent.ResultEdited("corrected"))

        assertEquals("corrected", viewModel.state.value.editedText)
    }

    @Test
    fun `the edited text survives a process death, even though the image does not`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("extracted"))
        val savedState = SavedStateHandle()

        val viewModel = viewModel(savedState)
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        // A fresh ViewModel over the same saved state is what process death looks like.
        val restored = viewModel(savedState)

        assertEquals(OcrState.Stage.Result, restored.state.value.stage)
        assertEquals("extracted", restored.state.value.editedText)
        // The bitmaps are deliberately not persisted, so the captures are gone.
        assertTrue(restored.state.value.captures.isEmpty())
    }

    @Test
    fun `copy, share and send-to-text-tools carry the edited text`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("payload"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        viewModel.effect.test {
            viewModel.onIntent(OcrIntent.CopyRequested)
            assertEquals(OcrEffect.CopyText("payload"), awaitItem())

            viewModel.onIntent(OcrIntent.ShareRequested)
            assertEquals(OcrEffect.ShareText("payload"), awaitItem())

            viewModel.onIntent(OcrIntent.SendToTextToolsRequested)
            assertEquals(OcrEffect.SendToTextTools("payload"), awaitItem())
        }
    }

    @Test
    fun `an oversized handover is shortened, and the user is told`() = runTest {
        stubLoad()
        val oversized = "x".repeat(TextToolsRoute.MAX_TEXT_LENGTH + 100)
        recognizer.enqueue(FakeTextRecognizer.found(oversized))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        viewModel.effect.test {
            viewModel.onIntent(OcrIntent.SendToTextToolsRequested)

            val effect = awaitItem() as OcrEffect.SendToTextTools
            assertEquals(TextToolsRoute.MAX_TEXT_LENGTH, effect.text.length)
        }
        // Silently handing over less than was extracted is how someone loses a paragraph without
        // noticing, so the shortening is announced.
        assertEquals(OcrNotice.TextTruncated, viewModel.state.value.notice)
    }

    @Test
    fun `a handover within the cap is passed through whole and says nothing`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("short enough"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        viewModel.effect.test {
            viewModel.onIntent(OcrIntent.SendToTextToolsRequested)
            assertEquals(OcrEffect.SendToTextTools("short enough"), awaitItem())
        }
        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `discarding one of several pages drops it and rejoins the rest`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("page one"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.AddAnotherPage)

        recognizer.enqueue(FakeTextRecognizer.found("page two"))
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        val secondPageId = viewModel.state.value.captures.last().id
        viewModel.onIntent(OcrIntent.CaptureRemoved(secondPageId))

        assertEquals(1, viewModel.state.value.captures.size)
        assertEquals("page one", viewModel.state.value.editedText)
        // Still a page left, so it stays where it was rather than bouncing to the viewfinder.
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
    }

    @Test
    fun `restarting clears the captures and the text`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("something"))
        val savedState = SavedStateHandle()

        val viewModel = viewModel(savedState)
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        viewModel.onIntent(OcrIntent.SelectionConfirmed)

        viewModel.onIntent(OcrIntent.Restarted)

        assertEquals(OcrState.Stage.Capture, viewModel.state.value.stage)
        assertEquals("", viewModel.state.value.editedText)
        assertTrue(viewModel.state.value.captures.isEmpty())
        // Cleared in saved state too, or the next process death would resurrect it.
        assertEquals("", savedState.get<String>("ocr_edited_text"))
    }

    @Test
    fun `restarting keeps the permission, which is not the user's work to redo`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(OcrIntent.PermissionResult(granted = true, shouldShowRationale = false))

        viewModel.onIntent(OcrIntent.Restarted)

        assertEquals(PermissionState.Granted, viewModel.state.value.permission)
    }

    @Test
    fun `a picked image goes through the same path as a capture`() = runTest {
        coEvery { imageLoader.load(any<android.net.Uri>()) } returns bitmap
        recognizer.enqueue(FakeTextRecognizer.found("from gallery"))

        val viewModel = viewModel()
        viewModel.onIntent(OcrIntent.ImagePicked(mockk()))
        advanceUntilIdle()

        // Selection, not straight to the result — a picked image gets block selection too.
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
        assertNull(viewModel.state.value.notice)
    }

    // --- Engine selection ------------------------------------------------------------------

    @Test
    fun `the selected engine is mirrored into state`() = runTest {
        val viewModel = viewModel()
        engine.value = OcrEngine.PaddleOcr
        advanceUntilIdle()

        assertEquals(OcrEngine.PaddleOcr, viewModel.state.value.engine)
    }

    @Test
    fun `changing engine re-reads the capture already on screen`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("read by ML Kit"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()
        assertEquals(1, recognizer.callCount)

        recognizer.reportedEngine = OcrEngine.PaddleOcr
        recognizer.enqueue(FakeTextRecognizer.found("read by PaddleOCR"))
        engine.value = OcrEngine.PaddleOcr
        advanceUntilIdle()

        // The retained bitmap is re-read rather than the user being asked to shoot it again.
        assertEquals(2, recognizer.callCount)
        assertEquals(
            "read by PaddleOCR",
            viewModel.state.value.currentCapture?.text?.blocks?.single()?.text,
        )
    }

    @Test
    fun `re-reading after an engine change stays on one capture`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("first"), FakeTextRecognizer.found("second"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        engine.value = OcrEngine.PaddleOcr
        advanceUntilIdle()

        // Replaced, not appended — a settings visit must not silently become a second page.
        assertEquals(1, viewModel.state.value.captures.size)
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
    }

    @Test
    fun `the initial engine value does not trigger a re-read`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("once"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        // The preference flow replays its stored value on subscription. Treating that as a change
        // would re-recognise on every return to the screen.
        assertEquals(1, recognizer.callCount)
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
    }

    @Test
    fun `nothing is re-read when there is no capture yet`() = runTest {
        viewModel()
        engine.value = OcrEngine.PaddleOcr
        advanceUntilIdle()

        assertEquals(0, recognizer.callCount)
    }

    @Test
    fun `falling back to another engine says so`() = runTest {
        stubLoad()
        engine.value = OcrEngine.PaddleOcr
        recognizer.enqueue(FakeTextRecognizer.found("text"))
        // PaddleOCR was asked for; ML Kit is what actually ran.
        recognizer.reportedEngine = OcrEngine.MlKit

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        assertEquals(OcrNotice.EngineUnavailable, viewModel.state.value.notice)
        // The text still arrives — the fallback is announced, not fatal.
        assertEquals(OcrState.Stage.Selection, viewModel.state.value.stage)
    }

    @Test
    fun `no notice when the engine that ran is the one selected`() = runTest {
        stubLoad()
        recognizer.enqueue(FakeTextRecognizer.found("text"))

        val viewModel = viewModel()
        viewModel.onFrameCaptured(frame())
        advanceUntilIdle()

        assertNull(viewModel.state.value.notice)
    }
}
