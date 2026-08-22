package com.minion.scaffold.feature.qrcreate.presentation.preview

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.minion.scaffold.core.emv.model.PayloadTag
import com.minion.scaffold.core.emv.model.PointOfInitiationMethod
import com.minion.scaffold.core.emv.model.TagInterpretation
import com.minion.scaffold.feature.qrcreate.R

/**
 * Human names and readable values for the tag breakdown.
 *
 * Kept in the presentation layer on purpose: `EmvTagCatalog` is a pure lookup that deliberately
 * carries no labels, so the translatable words for each tag live here as `@StringRes`, and the
 * decoded [TagInterpretation] the domain produces is turned into a sentence here rather than in the
 * domain.
 */

/** The tag's human name — "Currency", "Merchant account" — or a generic "Tag NN" fallback. */
@Composable
internal fun PayloadTag.label(): String {
    val res = labelRes()
    return if (res != null) stringResource(res) else stringResource(R.string.qrcreate_tag_generic, tag)
}

/**
 * The value in words: the decoded reading where the catalog has one, the raw value otherwise.
 *
 * Truncation is the chip's concern, not this — the detail line shows the value in full.
 */
@Composable
internal fun PayloadTag.describeValue(): String = when (val interpretation = interpretation) {
    is TagInterpretation.Currency -> interpretation.alphaCode?.let {
        stringResource(R.string.qrcreate_tag_value_named, interpretation.numericCode, it)
    } ?: interpretation.numericCode

    is TagInterpretation.MerchantCategory -> interpretation.name?.let {
        stringResource(R.string.qrcreate_tag_value_named, interpretation.code, it)
    } ?: interpretation.code

    is TagInterpretation.InitiationMethod -> when (interpretation.method) {
        PointOfInitiationMethod.STATIC -> stringResource(R.string.qrcreate_tag_initiation_static)
        PointOfInitiationMethod.DYNAMIC -> stringResource(R.string.qrcreate_tag_initiation_dynamic)
        PointOfInitiationMethod.UNKNOWN -> rawValue
    }

    is TagInterpretation.Checksum -> stringResource(
        if (interpretation.verification.passed) {
            R.string.qrcreate_tag_value_checksum_ok
        } else {
            R.string.qrcreate_tag_value_checksum_mismatch
        },
        interpretation.verification.expected,
    )

    is TagInterpretation.PayloadVersion,
    is TagInterpretation.Tip,
    TagInterpretation.None,
    -> rawValue
}

/** The `@StringRes` for this tag's name, or null when there is no specific name for it. */
@StringRes
// A tag-to-@StringRes lookup. Outside ignoreSingleWhenExpression only because the depth guard
// has to come first: the same two-character code names something different inside a template.
@Suppress("CyclomaticComplexMethod")
private fun PayloadTag.labelRes(): Int? {
    // Inside a template the two-character code means something else than at the top level, so the
    // sub-tag names are keyed on depth first.
    if (depth > 0) {
        return when (tag) {
            "00" -> R.string.qrcreate_tag_sub_00
            "01" -> R.string.qrcreate_tag_sub_01
            "02" -> R.string.qrcreate_tag_sub_02
            "03" -> R.string.qrcreate_tag_sub_03
            else -> null
        }
    }

    if (isTemplate) return R.string.qrcreate_tag_template

    return when (tag) {
        "00" -> R.string.qrcreate_tag_00
        "01" -> R.string.qrcreate_tag_01
        "52" -> R.string.qrcreate_tag_52
        "53" -> R.string.qrcreate_tag_53
        "54" -> R.string.qrcreate_tag_54
        "55" -> R.string.qrcreate_tag_55
        "56" -> R.string.qrcreate_tag_56
        "57" -> R.string.qrcreate_tag_57
        "58" -> R.string.qrcreate_tag_58
        "59" -> R.string.qrcreate_tag_59
        "60" -> R.string.qrcreate_tag_60
        "61" -> R.string.qrcreate_tag_61
        "62" -> R.string.qrcreate_tag_62
        "63" -> R.string.qrcreate_tag_63
        "64" -> R.string.qrcreate_tag_64
        else -> null
    }
}
