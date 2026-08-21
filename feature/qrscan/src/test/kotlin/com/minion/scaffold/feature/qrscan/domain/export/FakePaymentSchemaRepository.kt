package com.minion.scaffold.feature.qrscan.domain.export

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The schema repository, without DataStore or an `AssetManager`.
 *
 * The built-in template is read from **the file that actually ships** rather than from a copy
 * pasted in here. Assets are not on a unit test's classpath, but the module's own source tree is —
 * Gradle runs these with the module directory as the working directory — and reading it means the
 * golden test below is a regression test on the real asset. A second copy would let the shipped
 * contract change while the test kept agreeing with itself.
 */
internal class FakePaymentSchemaRepository(
    initial: String? = null,
) : PaymentSchemaRepository {

    private val state = MutableStateFlow(
        initial?.let { PaymentSchema(text = it, source = PaymentSchemaSource.Custom) }
            ?: PaymentSchema(text = builtInAsset, source = PaymentSchemaSource.BuiltIn),
    )

    override val activeSchema = state.asStateFlow()

    override suspend fun store(text: String, label: String) {
        state.value = PaymentSchema(
            text = text,
            source = PaymentSchemaSource.Custom,
            label = label,
        )
    }

    override suspend fun reset() {
        state.value = PaymentSchema(text = builtInAsset, source = PaymentSchemaSource.BuiltIn)
    }

    override suspend fun builtIn(): String = builtInAsset

    /** Puts the store into the state a template from a superseded format would leave it in. */
    fun makeOutdated() {
        state.value = state.value.copy(outdated = true)
    }

    companion object {

        /** The shipped default template, read from the source tree. */
        val builtInAsset: String by lazy {
            File("src/main/assets/default_payment_schema.json").readText()
        }
    }
}
