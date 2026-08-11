package com.minion.scaffold.core.sound.model

/**
 * Frequency weighting — which curve the signal is filtered through before its level is taken.
 *
 * [A] is what "sound level" means in every regulation and on every datasheet: a curve that
 * approximates human hearing's reduced sensitivity to low frequencies. [C] is nearly flat through
 * the bass and is used for peaks and for anything where A would under-report — traffic, machinery,
 * a subwoofer. [Z] is no weighting at all, and exists so that a suspicious reading can be checked
 * against the raw signal.
 *
 * The curves are defined by IEC 61672-1, which is also what
 * [com.minion.scaffold.core.sound.usecase.WeightingFilter] is tested against.
 */
enum class Weighting {
    A,
    C,
    Z,
}

/**
 * Time weighting — how quickly the displayed level follows the signal.
 *
 * Exponential averaging of the *mean square*, with these two time constants, is what a sound level
 * meter means by Fast and Slow. [Fast] tracks transients and is the default on virtually every
 * instrument; [Slow] gives a calmer number that is easier to read off in a fluctuating room, at the
 * cost of visibly lagging real events.
 *
 * This affects the displayed level, the session minimum and the session maximum. It deliberately
 * does **not** affect Leq, which is an energy average over the whole session and would be
 * double-averaged if it were fed a time-weighted value — see
 * [com.minion.scaffold.core.sound.usecase.AccumulateSessionUseCase].
 */
enum class TimeWeighting(val tauSeconds: Double) {
    Fast(0.125),
    Slow(1.0),
}
