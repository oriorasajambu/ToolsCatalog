package com.minion.scaffold.core.camera

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Play Services [Task] without pulling in `kotlinx-coroutines-play-services`.
 *
 * One suspension point is not worth another dependency, and this wrapper is the whole of what that
 * library would provide here. Cancellable, so a closed screen does not leave the continuation
 * hanging.
 *
 * Lives in `:core:camera` because both ML Kit consumers need it — it started file-private in
 * `:feature:qrscan`'s barcode decoder, and a second copy in the OCR recognizer is exactly the
 * duplication the module rules exist to prevent.
 */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancelQuietly() }
}

private fun CancellableContinuation<*>.cancelQuietly() {
    if (isActive) cancel()
}
