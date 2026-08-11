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

    /** Whether a JPEG's colour profile is kept, `true` by default. */
    val keepColourProfile: Flow<Boolean>

    /**
     * Sets whether to keep a JPEG's colour profile.
     *
     * @param keep Whether to retain the colour profile.
     */
    suspend fun setKeepColourProfile(keep: Boolean)
}

/** Observes the keep-colour-profile preference. */
internal class ObserveKeepColourProfileUseCase @Inject constructor(
    private val repository: ExifStripPreferencesRepository,
) {
    /** @return A [Flow] of the preference, `true` by default. */
    operator fun invoke(): Flow<Boolean> = repository.keepColourProfile
}

/** Sets the keep-colour-profile preference. */
internal class SetKeepColourProfileUseCase @Inject constructor(
    private val repository: ExifStripPreferencesRepository,
) {
    /** @param keep Whether to retain a JPEG's colour profile. */
    suspend operator fun invoke(keep: Boolean) = repository.setKeepColourProfile(keep)
}
