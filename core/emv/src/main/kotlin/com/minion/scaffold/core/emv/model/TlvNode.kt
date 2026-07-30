package com.minion.scaffold.core.emv.model

/**
 * One tag-length-value segment of an EMV payload.
 *
 * [length] is the length the payload *declared*, kept separate from `rawValue.length` so the
 * report can show what the merchant encoded rather than what was recovered. The parser rejects a
 * payload where the two would disagree, so in a successful parse they are equal — but the
 * declared value is what a reader is checking when something looks wrong.
 *
 * [children] is empty for a plain value and populated for a template (tag `26`–`51`, `62`, `64`,
 * `80`–`99`). Emptiness is the only signal needed: a template whose value does not parse as
 * well-formed TLV is reported flat rather than as a failure, because plenty of live payloads put
 * a plain identifier in a tag the specification reserves for a template.
 */
data class TlvNode(
    val tag: String,
    val length: Int,
    val rawValue: String,
    val children: List<TlvNode> = emptyList(),
)
