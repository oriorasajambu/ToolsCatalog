package com.minion.scaffold.feature.exifstrip.domain

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The one thing this tool remembers.
 *
 * Whether to keep a JPEG's colour profile. Not a privacy question — a profile describes a colour
 * space and identifies nobody — but a real tradeoff: dropping it makes the output strictly minimal,
 * and makes wide-gamut photos render washed out or oversaturated in colour-managed viewers.
 *
 * Defaults to keeping it, because a visible degradation the user did not ask for and cannot connect
 * to this tool is worse than a few hundred bytes of colour description they will never think about.
 */
internal interface ExifStripPreferencesRepository {
    val keepColourProfile: Flow<Boolean>
    suspend fun setKeepColourProfile(keep: Boolean)
}

internal class ObserveKeepColourProfileUseCase @Inject constructor(
    private val repository: ExifStripPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.keepColourProfile
}

internal class SetKeepColourProfileUseCase @Inject constructor(
    private val repository: ExifStripPreferencesRepository,
) {
    suspend operator fun invoke(keep: Boolean) = repository.setKeepColourProfile(keep)
}
