package com.minion.scaffold.core.ui.permission

/**
 * What the system has said about a runtime permission.
 *
 * [Denied] and [PermanentlyDenied] are separate because the recovery differs: one is another
 * request away, the other can only be undone in system settings, and offering the wrong button
 * either wastes a tap or asks the user to hunt through Settings when a dialog would have done.
 *
 * Shared rather than per-feature because the *logic* is identical everywhere — the camera and
 * location gates were already byte-for-byte copies of each other, cross-referencing in comments.
 * What legitimately differs between features is the rationale UI, and that deliberately stays in
 * each feature: `:feature:qrscan` offers "paste a payload instead" beside its prompt, while
 * `:feature:weather` has no equivalent fallback to offer.
 */
enum class PermissionState {

    /** Not yet asked in this session. Render nothing — anything here flashes behind the dialog. */
    Unknown,

    /** The permission is granted. */
    Granted,

    /** Refused, but the system will still show the dialog. */
    Denied,

    /** Refused to the point where only Settings can grant it. */
    PermanentlyDenied,
    ;

    companion object {

        /**
         * Resolves a permission result into a state.
         *
         * [shouldShowRationale] is only meaningful *after* a request — it is also false before the
         * first one — which is why this is reached from a result and never from a bare check.
         * Calling it with the answer to a cold `checkSelfPermission` would report every
         * never-asked permission as [PermanentlyDenied].
         *
         * The value has to be read at the call site because it needs an `Activity`, which has no
         * business being in a ViewModel.
         *
         * @param granted            Whether the permission is currently granted.
         * @param shouldShowRationale The system's rationale flag, read after a request.
         * @return [Granted], [Denied], or [PermanentlyDenied].
         */
        fun resolve(granted: Boolean, shouldShowRationale: Boolean): PermissionState = when {
            granted -> Granted
            shouldShowRationale -> Denied
            else -> PermanentlyDenied
        }
    }
}
