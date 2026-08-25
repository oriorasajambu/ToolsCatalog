package com.minion.scaffold.feature.checksum.presentation

import app.cash.turbine.test
import com.minion.scaffold.core.testing.MainDispatcherRule
import com.minion.scaffold.core.text.model.TextOperation
import com.minion.scaffold.core.text.usecase.TransformTextUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The real [TransformTextUseCase], not a fake.
 *
 * It has no dependencies and no I/O — constructing it is as cheap as constructing a mock, and using
 * it means these tests assert the digests the app actually shows rather than whatever a stub was
 * told to return. The expected values below are the published digests of `"abc"`, so a change to
 * `:core:text`'s hashing fails here too, which is the point of sharing the use case at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChecksumViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = ChecksumViewModel(transformText = TransformTextUseCase())

    @Test
    fun `starts empty, with nothing to compare`() {
        assertEquals("", viewModel.state.value.digest)
        assertEquals(Verdict.NOT_COMPARED, viewModel.state.value.verdict)
    }

    @Test
    fun `digest recomputes as the input changes`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))

        assertEquals(SHA256_OF_ABC, viewModel.state.value.digest)
    }

    @Test
    fun `digest recomputes as the algorithm changes`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.AlgorithmChanged(TextOperation.MD5))

        assertEquals(MD5_OF_ABC, viewModel.state.value.digest)
    }

    /** An empty input is a blank field, not the digest of the empty string. */
    @Test
    fun `clearing the input clears the digest`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.InputChanged(""))

        assertEquals("", viewModel.state.value.digest)
        assertEquals(Verdict.NOT_COMPARED, viewModel.state.value.verdict)
    }

    @Test
    fun `an expected digest that matches reads as a match`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.ExpectedChanged(SHA256_OF_ABC))

        assertEquals(Verdict.MATCH, viewModel.state.value.verdict)
    }

    /** A digest copied off a release page is often uppercase, and drags whitespace with it. */
    @Test
    fun `case and surrounding whitespace do not make a mismatch`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.ExpectedChanged("  ${SHA256_OF_ABC.uppercase()}\n"))

        assertEquals(Verdict.MATCH, viewModel.state.value.verdict)
    }

    @Test
    fun `one wrong character reads as a mismatch`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.ExpectedChanged(SHA256_OF_ABC.dropLast(1) + "0"))

        assertEquals(Verdict.MISMATCH, viewModel.state.value.verdict)
    }

    /** The verdict follows the algorithm, or a match would survive switching to a different hash. */
    @Test
    fun `switching algorithm re-runs the comparison`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.ExpectedChanged(SHA256_OF_ABC))
        viewModel.onIntent(ChecksumIntent.AlgorithmChanged(TextOperation.SHA1))

        assertEquals(SHA1_OF_ABC, viewModel.state.value.digest)
        assertEquals(Verdict.MISMATCH, viewModel.state.value.verdict)
    }

    /** An empty expected field is "nothing to compare", never a mismatch. */
    @Test
    fun `an empty expected digest is not a mismatch`() {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))
        viewModel.onIntent(ChecksumIntent.ExpectedChanged("   "))

        assertEquals(Verdict.NOT_COMPARED, viewModel.state.value.verdict)
    }

    @Test
    fun `copying emits the current digest`() = runTest {
        viewModel.onIntent(ChecksumIntent.InputChanged("abc"))

        viewModel.effect.test {
            viewModel.onIntent(ChecksumIntent.CopyDigestRequested)
            advanceUntilIdle()

            assertEquals(ChecksumEffect.CopyDigest(SHA256_OF_ABC), awaitItem())
        }
    }

    @Test
    fun `copying an empty digest emits nothing`() = runTest {
        viewModel.effect.test {
            viewModel.onIntent(ChecksumIntent.CopyDigestRequested)
            advanceUntilIdle()

            expectNoEvents()
        }
    }

    private companion object {
        const val MD5_OF_ABC = "900150983cd24fb0d6963f7d28e17f72"
        const val SHA1_OF_ABC = "a9993e364706816aba3e25717850c26c9cd0d89d"
        const val SHA256_OF_ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }
}
