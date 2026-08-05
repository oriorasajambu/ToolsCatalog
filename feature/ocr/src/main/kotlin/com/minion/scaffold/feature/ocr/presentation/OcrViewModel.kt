package com.minion.scaffold.feature.ocr.presentation

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.camera.CapturedFrame
import com.minion.scaffold.core.navigation.TextToolsRoute
import com.minion.scaffold.core.ocr.model.RecognizedText
import com.minion.scaffold.core.ocr.usecase.AssembleTextUseCase
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.core.ui.permission.PermissionState
import com.minion.scaffold.feature.ocr.data.ImageLoader
import com.minion.scaffold.feature.ocr.data.OcrResult
import com.minion.scaffold.feature.ocr.data.Recognition
import com.minion.scaffold.feature.ocr.data.TextRecognizer
import com.minion.scaffold.feature.ocr.domain.ObserveOcrEngineUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OcrViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageLoader: ImageLoader,
    private val textRecognizer: TextRecognizer,
    private val assembleText: AssembleTextUseCase,
    observeOcrEngine: ObserveOcrEngineUseCase,
) : MviViewModel<OcrState, OcrIntent, OcrEffect>(OcrState()) {

    init {
        // `drop(1)` because the flow replays the stored value on subscription, and that first
        // emission is not a change — reacting to it would re-recognise a capture on every return to
        // the screen.
        observeOcrEngine()
            .onEach { engine -> reduce { copy(engine = engine) } }
            .drop(1)
            .onEach { reRecogniseCurrentCapture() }
            .launchIn(viewModelScope)

        // Only the text is restored, not the captures — bitmaps are far too large for saved state
        // and are deliberately never persisted anywhere. A process death during selection costs
        // the image and the block overlays but keeps whatever text had already been extracted,
        // which is where the user's work actually is.
        savedStateHandle.get<String>(KEY_EDITED_TEXT)
            ?.takeIf { it.isNotEmpty() }
            ?.let { restored -> reduce { copy(stage = OcrState.Stage.Result, editedText = restored) } }
    }

    override fun onIntent(intent: OcrIntent) {
        when (intent) {
            is OcrIntent.PermissionResult -> reduce {
                copy(
                    permission = PermissionState.resolve(
                        granted = intent.granted,
                        shouldShowRationale = intent.shouldShowRationale,
                    ),
                )
            }

            OcrIntent.AppSettingsRequested -> emit(OcrEffect.OpenAppSettings)

            is OcrIntent.ImagePicked -> recogniseFrom { imageLoader.load(intent.uri) }

            is OcrIntent.HintBoxesChanged -> reduce { copy(hintBoxes = intent.boxes) }

            is OcrIntent.BlockToggled -> toggleBlock(intent.blockId)

            OcrIntent.SelectAllToggled -> toggleSelectAll()

            OcrIntent.RotateAndRetry -> rotateAndRetry()

            OcrIntent.AddAnotherPage -> reduce {
                copy(stage = OcrState.Stage.Capture, hintBoxes = emptyList())
            }

            OcrIntent.SelectionConfirmed -> confirmSelection()

            is OcrIntent.CaptureRemoved -> removeCapture(intent.captureId)

            is OcrIntent.ResultEdited -> {
                savedStateHandle[KEY_EDITED_TEXT] = intent.text
                reduce { copy(editedText = intent.text) }
            }

            OcrIntent.CopyRequested -> emit(OcrEffect.CopyText(currentState.editedText))

            OcrIntent.ShareRequested -> emit(OcrEffect.ShareText(currentState.editedText))

            OcrIntent.SendToTextToolsRequested -> sendToTextTools()

            OcrIntent.Restarted -> restart()

            OcrIntent.NoticeDismissed -> reduce { copy(notice = null) }
        }
    }

    /**
     * Called by the screen once the shutter has produced a frame.
     *
     * A method rather than an intent: an intent carrying a multi-megabyte JPEG would put the image
     * into the one channel that exists to describe *what the user did*. The frame belongs to the
     * camera, which lives in the composable.
     */
    fun onFrameCaptured(frame: CapturedFrame?) {
        if (frame == null) {
            reduce { copy(notice = OcrNotice.CaptureFailed) }
            return
        }

        recogniseFrom { imageLoader.load(frame.jpegBytes, frame.rotationDegrees) }
    }

    /**
     * The single recognition path, shared by the camera and the gallery.
     *
     * Both decode to a bitmap first and recognise from there, which is what lets the selection
     * overlay draw the very image that was read — a gallery pick that recognised straight from a
     * `Uri` would have no bitmap to show and would have to skip selection entirely.
     */
    private fun recogniseFrom(load: suspend () -> Bitmap?) = viewModelScope.launch {
        reduce { copy(isRecognising = true, notice = null) }

        val bitmap = load()
        if (bitmap == null) {
            reduce { copy(isRecognising = false, notice = OcrNotice.ImageUnreadable) }
            return@launch
        }

        val recognition = textRecognizer.recognize(bitmap)
        noteEngineFallback(recognition)

        when (val result = recognition.result) {
            is OcrResult.Found -> addCapture(bitmap, result.text)

            // The capture is kept even with no text in it, so rotate-and-retry has something to
            // work with — a sideways photo lands here, and re-shooting it would be the wrong ask.
            OcrResult.NoText -> {
                addCapture(bitmap, RecognizedText.EMPTY)
                reduce { copy(notice = OcrNotice.NoTextFound) }
            }

            OcrResult.Unreadable ->
                reduce { copy(isRecognising = false, notice = OcrNotice.ImageUnreadable) }
        }
    }

    /**
     * Re-reads the capture already on screen with the newly-selected engine.
     *
     * The bitmap is still in memory, so this costs nothing to offer and is the whole point of being
     * able to switch: leave to settings, come back, and see the same photograph read by the other
     * engine. Selection resets because block ids are regenerated by every recognition.
     */
    private fun reRecogniseCurrentCapture() {
        val capture = currentState.currentCapture ?: return

        viewModelScope.launch {
            reduce { copy(isRecognising = true, notice = null) }

            val recognition = textRecognizer.recognize(capture.bitmap)
            noteEngineFallback(recognition)

            val text = when (val result = recognition.result) {
                is OcrResult.Found -> result.text
                OcrResult.NoText -> RecognizedText.EMPTY
                OcrResult.Unreadable -> {
                    reduce { copy(isRecognising = false, notice = OcrNotice.ImageUnreadable) }
                    return@launch
                }
            }

            replaceCurrentCapture(
                capture.copy(
                    text = text,
                    selectedBlockIds = text.blocks.mapTo(mutableSetOf()) { it.id },
                ),
            )
            reduce { copy(isRecognising = false) }
            if (text.isEmpty) reduce { copy(notice = OcrNotice.NoTextFound) }
        }
    }

    /**
     * Says so when the engine that ran was not the one selected.
     *
     * Only fires on a genuine mismatch, so the ordinary case is silent. Without it the fallback
     * would be invisible and the setting would be quietly lying about what read the image.
     */
    private fun noteEngineFallback(recognition: Recognition) {
        if (recognition.engine != currentState.engine) {
            reduce { copy(notice = OcrNotice.EngineUnavailable) }
        }
    }

    private fun addCapture(bitmap: Bitmap, text: RecognizedText) {
        val capture = CaptureUi(
            id = System.nanoTime().toString(),
            bitmap = bitmap,
            text = text,
            // Everything selected by default: the common case is wanting all of it, and
            // deselecting a few is less work than selecting many.
            selectedBlockIds = text.blocks.mapTo(mutableSetOf()) { it.id },
        )

        reduce {
            copy(
                isRecognising = false,
                stage = OcrState.Stage.Selection,
                captures = captures + capture,
                hintBoxes = emptyList(),
            )
        }
    }

    private fun toggleBlock(blockId: String) = updateCurrentCapture { capture ->
        capture.copy(
            selectedBlockIds = capture.selectedBlockIds.toMutableSet().apply {
                if (!add(blockId)) remove(blockId)
            },
        )
    }

    private fun toggleSelectAll() = updateCurrentCapture { capture ->
        val allSelected = capture.selectedBlockIds.size == capture.text.blocks.size
        capture.copy(
            selectedBlockIds = if (allSelected) {
                emptySet()
            } else {
                capture.text.blocks.mapTo(mutableSetOf()) { it.id }
            },
        )
    }

    private fun rotateAndRetry() {
        val capture = currentState.currentCapture ?: return

        viewModelScope.launch {
            reduce { copy(isRecognising = true, notice = null) }

            val rotated = imageLoader.rotateQuarterTurn(capture.bitmap)
            val recognition = textRecognizer.recognize(rotated)
            noteEngineFallback(recognition)

            val text = when (val result = recognition.result) {
                is OcrResult.Found -> result.text
                OcrResult.NoText -> RecognizedText.EMPTY
                OcrResult.Unreadable -> {
                    reduce { copy(isRecognising = false, notice = OcrNotice.ImageUnreadable) }
                    return@launch
                }
            }

            replaceCurrentCapture(
                capture.copy(
                    bitmap = rotated,
                    text = text,
                    selectedBlockIds = text.blocks.mapTo(mutableSetOf()) { it.id },
                ),
            )
            if (text.isEmpty) reduce { copy(notice = OcrNotice.NoTextFound) }
        }
    }

    /**
     * Hands the text over, shortened if it will not fit.
     *
     * Truncation lives here rather than in the navigation extension so that it can *say so* —
     * silently delivering less than the user extracted is the kind of thing nobody notices until
     * they are missing a paragraph. The ceiling itself belongs to the route, which is what owns
     * the transport (see [TextToolsRoute.MAX_TEXT_LENGTH]).
     */
    private fun sendToTextTools() {
        val text = currentState.editedText

        if (text.length > TextToolsRoute.MAX_TEXT_LENGTH) {
            reduce { copy(notice = OcrNotice.TextTruncated) }
        }

        emit(OcrEffect.SendToTextTools(text.take(TextToolsRoute.MAX_TEXT_LENGTH)))
    }

    private fun confirmSelection() {
        val text = assembleText.across(
            currentState.captures.map { it.text to it.selectedBlockIds },
        )
        savedStateHandle[KEY_EDITED_TEXT] = text
        reduce { copy(stage = OcrState.Stage.Result, editedText = text) }
    }

    private fun removeCapture(captureId: String) {
        val remaining = currentState.captures.filterNot { it.id == captureId }
        val text = assembleText.across(remaining.map { it.text to it.selectedBlockIds })
        savedStateHandle[KEY_EDITED_TEXT] = text

        reduce {
            copy(
                captures = remaining,
                editedText = text,
                // Nothing left to show a result for, so back to the viewfinder rather than an
                // empty editor with no way to explain itself.
                stage = if (remaining.isEmpty()) OcrState.Stage.Capture else stage,
            )
        }
    }

    private fun restart() {
        savedStateHandle[KEY_EDITED_TEXT] = ""
        reduce { OcrState(permission = permission) }
    }

    private fun updateCurrentCapture(transform: (CaptureUi) -> CaptureUi) {
        val capture = currentState.currentCapture ?: return
        replaceCurrentCapture(transform(capture))
    }

    private fun replaceCurrentCapture(updated: CaptureUi) {
        reduce {
            copy(
                isRecognising = false,
                captures = captures.dropLast(1) + updated,
            )
        }
    }

    private fun emit(effect: OcrEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }

    /**
     * Drops PaddleOCR's ONNX sessions when the screen goes away.
     *
     * Roughly 22MB of weights plus the runtime's arena, held for the tool rather than for the app —
     * keeping them alive after the user has left would pin that memory on a device that is very
     * likely still running a camera somewhere. Costs a session rebuild on the next visit, which is
     * the right side of the trade for a tool opened occasionally.
     */
    override fun onCleared() {
        textRecognizer.release()
        super.onCleared()
    }

    private companion object {
        const val KEY_EDITED_TEXT = "ocr_edited_text"
    }
}
