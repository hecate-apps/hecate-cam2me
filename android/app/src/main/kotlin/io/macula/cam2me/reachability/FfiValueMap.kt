package io.macula.cam2me.reachability

import io.macula.sdk.FfiMapEntry
import io.macula.sdk.FfiValue

/**
 * `FfiValue.Fields` is an ordered `List<FfiMapEntry>`, not a Kotlin `Map` --
 * mirrors `cbor::Value::Map`'s own `Vec<(Value, Value)>` exactly, since a
 * map's keys aren't always text (see `FfiValue.Fields`'s own doc in
 * macula-rust-sdk-ffi). Every payload cam2me itself builds uses text keys
 * only, so these helpers are scoped to that case rather than the fully
 * general one.
 */
fun ffiFields(vararg pairs: Pair<String, FfiValue>): FfiValue =
    FfiValue.Fields(pairs.map { (k, v) -> FfiMapEntry(FfiValue.Text(k), v) })

/** Looks up a text-keyed field in an `FfiValue.Fields`. Null if this isn't
 * a Fields value, or the key isn't present. */
fun FfiValue.field(key: String): FfiValue? {
    val fields = (this as? FfiValue.Fields)?.v1 ?: return null
    return fields.firstOrNull { (it.key as? FfiValue.Text)?.v1 == key }?.value
}

fun FfiValue.asText(): String? = (this as? FfiValue.Text)?.v1

fun FfiValue.asDouble(): Double? = when (this) {
    is FfiValue.Float -> v1
    is FfiValue.Int -> v1.toDouble()
    else -> null
}

fun FfiValue.asInt(): Long? = (this as? FfiValue.Int)?.v1

fun FfiValue.asItems(): List<FfiValue>? = (this as? FfiValue.Items)?.v1
