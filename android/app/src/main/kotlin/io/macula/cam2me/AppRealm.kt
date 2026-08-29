package io.macula.cam2me

/**
 * Which macula realm this app is a member of -- every hecate-service on
 * the macula-demo fleet already shares this one (io.macula's own 64-hex
 * tag). Reused here rather than minting a new one: nothing about
 * presence or station discovery needs isolation from that fleet, and
 * every existing service already proves this realm round-trips end to
 * end (advertise, subscribe, publish) against the live station.
 *
 * Used by both `reachability` (the hecate_stations.list_stations call)
 * and `presence` (publish/subscribe) -- app-wide config, not owned by
 * either slice, so it lives here rather than faking a dependency from
 * one into the other.
 */
private const val APP_REALM_HEX =
    "074acb6cb190d8ef79fdbdd8e8e76d53f6292c181fd23f4d3998560f9a94e8e3"

val APP_REALM: ByteArray = hexToBytes(APP_REALM_HEX)

private fun hexToBytes(hex: String): ByteArray {
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val idx = i * 2
        out[i] = ((Character.digit(hex[idx], 16) shl 4) + Character.digit(hex[idx + 1], 16)).toByte()
    }
    return out
}
