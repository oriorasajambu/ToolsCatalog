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
import com.minion.scaffold.feature.ocr.data.TextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class OcrViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val imageLoader: ImageLoader,
    private val textRecognizer: TextRecognizer,
    private val assembleText: AssembleTextUseCase,
) : MviViewModel<OcrState, OcrIntent, OcrEffect>(OcrState()) {

    init {
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

        when (val result = textRecognizer.recognize(bitmap)) {
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
            val text = when (val result = textRecognizer.recognize(rotated)) {
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

    private companion object {
        const val KEY_EDITED_TEXT = "ocr_edited_text"
    }
}
