package com.minion.scaffold.core.text.usecase

import com.minion.scaffold.core.text.model.CharacterClass
import com.minion.scaffold.core.text.model.GenerateResult
import com.minion.scaffold.core.text.model.PasswordProblem
import com.minion.scaffold.core.text.model.PasswordSpec
import java.util.Random
import java.util.UUID
import javax.inject.Inject

/**
 * Generates a UUID, a password, or random hex.
 *
 * The [random] is injected — production binds a `SecureRandom`, a test passes a seeded `Random` so
 * the output is deterministic to assert against. This is the one genuinely security-critical seam
 * in the whole feature: a password drawn from a predictable generator is not a password, so the
 * binding is a cryptographic source and the injection exists so a test can prove the shape without
 * weakening the real thing.
 */
class GenerateTextUseCase @Inject constructor(
    private val random: Random,
) {

    /**
     * A random version-4 UUID.
     *
     * @return The UUID in its canonical hyphenated string form.
     */
    fun uuid(): String = UUID.randomUUID().toString()

    /**
     * [byteCount] random bytes, as lowercase hex, drawn from the injected [random].
     *
     * @param byteCount How many random bytes to draw.
     * @return The bytes as a hex string, two characters per byte.
     */
    fun randomHex(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    /**
     * A random password matching [spec], with one character from each selected class guaranteed.
     *
     * @param spec The length and character classes to draw from.
     * @return [GenerateResult.Success] with the password, or [GenerateResult.Invalid] when [spec]
     *         selects no classes or is too short to honour "one of each".
     */
    fun password(spec: PasswordSpec): GenerateResult {
        if (spec.classes.isEmpty()) {
            return GenerateResult.Invalid(PasswordProblem.NO_CHARACTER_CLASS)
        }
        // Below one, or too short to fit one character from each selected class — the "one of each"
        // guarantee below cannot be met otherwise.
        if (spec.length < spec.classes.size) {
            return GenerateResult.Invalid(PasswordProblem.LENGTH_TOO_SHORT)
        }

        val pools = spec.classes.map(CharacterClass::pool)
        val all = pools.flatMap { it.toList() }

        // One from each class first, so every selected class is guaranteed present, then fill the
        // rest from the union — and shuffle, or the guaranteed characters would always sit at the
        // front in class order.
        val characters = buildList {
            pools.forEach { add(it.random(random)) }
            repeat(spec.length - pools.size) { add(all.random(random)) }
        }

        return GenerateResult.Success(characters.shuffled(random).joinToString(separator = ""))
    }

    private fun String.random(random: Random): Char = this[random.nextInt(length)]

    private fun List<Char>.random(random: Random): Char = this[random.nextInt(size)]

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}

private fun CharacterClass.pool(): String = when (this) {
    CharacterClass.LOWERCASE -> "abcdefghijklmnopqrstuvwxyz"
    CharacterClass.UPPERCASE -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    CharacterClass.DIGITS -> "0123456789"
    // No ambiguous or shell-hostile characters — a password nobody can type from a printout is a
    // support call, not security.
    CharacterClass.SYMBOLS -> "!@#\$%^&*-_=+?"
}
