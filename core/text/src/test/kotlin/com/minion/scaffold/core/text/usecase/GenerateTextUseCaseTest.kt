package com.minion.scaffold.core.text.usecase

import com.minion.scaffold.core.text.model.CharacterClass
import com.minion.scaffold.core.text.model.GenerateResult
import com.minion.scaffold.core.text.model.PasswordProblem
import com.minion.scaffold.core.text.model.PasswordSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Random
import org.junit.Test

/**
 * The RNG is seeded, so output is deterministic to assert against — but every assertion is a
 * *property* (length, membership, one-of-each), not a fixed string. The properties hold for any
 * seed, which is what makes them real guarantees rather than a snapshot of one draw.
 */
internal class GenerateTextUseCaseTest {

    private val generate = GenerateTextUseCase(Random(SEED))

    @Test
    fun `a uuid is well-formed`() {
        assertTrue(generate.uuid().matches(UUID_SHAPE))
    }

    @Test
    fun `random hex is twice the byte count and all hexadecimal`() {
        val hex = generate.randomHex(byteCount = 16)

        assertEquals(32, hex.length)
        assertTrue(hex.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `a password honours its length`() {
        val password = passwordOrFail(length = 24, classes = CharacterClass.entries.toSet())

        assertEquals(24, password.length)
    }

    @Test
    fun `a password contains at least one of every selected class`() {
        val password = passwordOrFail(
            length = 20,
            classes = setOf(CharacterClass.LOWERCASE, CharacterClass.DIGITS),
        )

        assertTrue("expected a lowercase letter", password.any { it in 'a'..'z' })
        assertTrue("expected a digit", password.any(Char::isDigit))
        assertTrue("expected no uppercase", password.none { it in 'A'..'Z' })
    }

    @Test
    fun `a password draws only from the selected classes`() {
        val password = passwordOrFail(length = 40, classes = setOf(CharacterClass.LOWERCASE))

        assertTrue(password.all { it in 'a'..'z' })
    }

    /** Property over many seeds: the "one of each" guarantee is not a lucky single draw. */
    @Test
    fun `every selected class appears regardless of seed`() {
        repeat(200) { seed ->
            val result = GenerateTextUseCase(Random(seed.toLong())).password(
                PasswordSpec(length = 4, classes = CharacterClass.entries.toSet()),
            )
            val value = (result as GenerateResult.Success).value

            assertTrue(value.any { it in 'a'..'z' })
            assertTrue(value.any { it in 'A'..'Z' })
            assertTrue(value.any(Char::isDigit))
            assertTrue(value.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `no selected class is rejected`() {
        val result = generate.password(PasswordSpec(length = 12, classes = emptySet()))

        assertEquals(GenerateResult.Invalid(PasswordProblem.NO_CHARACTER_CLASS), result)
    }

    /** Four classes cannot fit in three characters while keeping one of each. */
    @Test
    fun `a length below the class count is rejected`() {
        val result = generate.password(
            PasswordSpec(length = 3, classes = CharacterClass.entries.toSet()),
        )

        assertEquals(GenerateResult.Invalid(PasswordProblem.LENGTH_TOO_SHORT), result)
    }

    private fun passwordOrFail(length: Int, classes: Set<CharacterClass>): String =
        when (val result = generate.password(PasswordSpec(length, classes))) {
            is GenerateResult.Success -> result.value
            is GenerateResult.Invalid -> throw AssertionError("expected Success but was ${result.reason}")
        }

    private companion object {
        const val SEED = 42L
        val UUID_SHAPE =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
