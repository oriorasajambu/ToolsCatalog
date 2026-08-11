package com.minion.scaffold.feature.exifstrip.presentation

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.minion.scaffold.core.exif.model.StripFailure
import com.minion.scaffold.core.ui.mvi.MviViewModel
import com.minion.scaffold.feature.exifstrip.domain.CleanCopyExporter
import com.minion.scaffold.feature.exifstrip.domain.ExportResult
import com.minion.scaffold.feature.exifstrip.domain.InspectedPhoto
import com.minion.scaffold.feature.exifstrip.domain.InspectionResult
import com.minion.scaffold.feature.exifstrip.domain.ObserveKeepColourProfileUseCase
import com.minion.scaffold.feature.exifstrip.domain.PhotoInspector
import com.minion.scaffold.feature.exifstrip.domain.SetKeepColourProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Inspect, then export.
 *
 * The picked photo's bytes are held here for the life of the screen, because the strip needs them
 * and re-reading the `Uri` at export time would risk the picker's grant having expired in between.
 * They are dropped on [ExifStripIntent.Cleared] and never written anywhere but the working file.
 */
@HiltViewModel
internal class ExifStripViewModel @Inject constructor(
    private val photoInspector: PhotoInspector,
    private val exporter: CleanCopyExporter,
    observeKeepColourProfile: ObserveKeepColourProfileUseCase,
    private val setKeepColourProfile: SetKeepColourProfileUseCase,
) : MviViewModel<ExifStripState, ExifStripIntent, ExifStripEffect>(ExifStripState()) {

    private var photo: InspectedPhoto? = null

    init {
        observeKeepColourProfile()
            .onEach { keep -> reduce { copy(keepColourProfile = keep) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: ExifStripIntent) {
        when (intent) {
            is ExifStripIntent.PhotoPicked -> inspect(intent.uri)

            ExifStripIntent.Cleared -> {
                photo = null
                reduce { copy(content = ExifStripState.Content.Empty, export = null) }
            }

            ExifStripIntent.ExportRequested -> export(convert = false)
            ExifStripIntent.ConvertRequested -> export(convert = true)

            ExifStripIntent.ShareRequested -> currentState.export?.let { export ->
                emit(ExifStripEffect.Share(export.uri))
            }

            is ExifStripIntent.OpenInMapsRequested ->
                emit(ExifStripEffect.OpenInMaps(intent.latitude, intent.longitude))

            is ExifStripIntent.CopyRequested ->
                emit(ExifStripEffect.Copy(intent.label, intent.value))

            is ExifStripIntent.KeepColourProfileChanged -> viewModelScope.launch {
                setKeepColourProfile(intent.keep)
            }
        }
    }

    private fun inspect(uri: Uri) {
        // A new pick invalidates whatever was exported: the share button must never point at the
        // previous photo's clean copy, which would be a quietly catastrophic thing to get wrong.
        photo = null
        reduce { copy(content = ExifStripState.Content.Loading, export = null) }

        viewModelScope.launch {
            when (val result = photoInspector.inspect(uri)) {
                is InspectionResult.Success -> {
                    photo = result.photo
                    reduce {
                        copy(
                            content = ExifStripState.Content.Loaded(
                                uri = uri,
                                displayName = result.photo.displayName,
                                metadata = result.photo.metadata,
                                convertibleFormat = null,
                            ),
                        )
                    }
                    // Planning now rather than at export time means an unstrippable container is
                    // reported while the user is still looking at what was found, not after they
                    // have pressed a button that then declines to do anything.
                    probeContainer()
                }

                InspectionResult.Unreadable -> fail(ExifStripState.FailureReason.Unreadable)

                is InspectionResult.TooLarge -> fail(ExifStripState.FailureReason.TooLarge)
            }
        }
    }

    /**
     * Asks whether the container can be stripped, without writing anything.
     *
     * Done at inspection time so an unstrippable format is reported while the user is still reading
     * what was found — rather than after they press an export button that then declines. A HEIC is
     * still worth inspecting: the metadata reads perfectly, and seeing the GPS is most of the value
     * even when the lossless path is unavailable.
     */
    private suspend fun probeContainer() {
        val current = photo ?: return
        val probe = exporter.probe(current, currentState.keepColourProfile)

        if (probe.failure != null) {
            val format = (probe.failure as? StripFailure.UnsupportedContainer)?.describedAs
            if (format == null) {
                fail(ExifStripState.FailureReason.NotAnImage)
                return
            }

            reduce {
                copy(
                    content = when (val existing = content) {
                        is ExifStripState.Content.Loaded -> existing.copy(convertibleFormat = format)
                        else -> existing
                    },
                )
            }
            return
        }

        // The container's own account of what it holds, merged in beside the Exif reader's. See
        // Content.Loaded.containerBlocks for why this is not optional.
        reduce {
            copy(
                content = when (val existing = content) {
                    is ExifStripState.Content.Loaded -> existing.copy(
                        containerBlocks = probe.removable,
                        trailing = probe.trailing,
                    )

                    else -> existing
                },
            )
        }
    }

    private fun export(convert: Boolean) {
        val current = photo ?: return
        if (currentState.exporting) return

        reduce { copy(exporting = true) }

        viewModelScope.launch {
            val result = if (convert) {
                exporter.convertToCleanJpeg(current)
            } else {
                exporter.export(current, currentState.keepColourProfile)
            }

            reduce { copy(exporting = false) }

            when (result) {
                is ExportResult.Success -> {
                    reduce {
                        copy(
                            export = ExifStripState.ExportState(
                                uri = result.uri,
                                fileName = result.fileName,
                                byteCount = result.byteCount,
                                originalByteCount = result.originalByteCount,
                                removed = result.removed,
                                retained = result.retained,
                                trailing = result.trailing,
                                recompressed = result.recompressed,
                            ),
                        )
                    }

                    if (result.removed.isEmpty() && result.trailing == null && !result.recompressed) {
                        emit(ExifStripEffect.Notice(ExifStripNotice.NothingToRemove))
                    }
                }

                // Blocking, not a warning. The file is not offered, because the entire value of the
                // operation was the claim that it worked.
                is ExportResult.VerificationFailed ->
                    fail(ExifStripState.FailureReason.VerificationFailed)

                is ExportResult.Rejected -> fail(ExifStripState.FailureReason.NotAnImage)

                ExportResult.WriteFailed -> fail(ExifStripState.FailureReason.WriteFailed)
            }
        }
    }

    private fun fail(reason: ExifStripState.FailureReason) {
        reduce { copy(content = ExifStripState.Content.Failed(reason), export = null) }
    }

    private fun emit(effect: ExifStripEffect) {
        viewModelScope.launch { emitEffect(effect) }
    }
}
