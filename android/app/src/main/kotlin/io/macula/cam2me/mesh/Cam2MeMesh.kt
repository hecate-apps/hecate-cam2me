package io.macula.cam2me.mesh

/**
 * The realm every hecate-service on the macula-demo fleet already shares
 * (io.macula's own 64-hex tag). Reused here rather than minting a new one:
 * nothing about presence or dialing needs isolation from that fleet, and
 * every existing service already proves this realm round-trips end to end
 * (advertise, subscribe, publish) against the live station.
 */
private const val CAM2ME_REALM_HEX =
    "074acb6cb190d8ef79fdbdd8e8e76d53f6292c181fd23f4d3998560f9a94e8e3"

val CAM2ME_REALM: ByteArray = hexToBytes(CAM2ME_REALM_HEX)

/**
 * Type-level topic, no ids -- every device's own hashed phone number, node
 * id and station live in the payload, never in the topic name. See
 * PresenceHeartbeat.
 */
const val PRESENCE_TOPIC = "cam2me.presence"

private fun hexToBytes(hex: String): ByteArray {
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val idx = i * 2
        out[i] = ((Character.digit(hex[idx], 16) shl 4) + Character.digit(hex[idx + 1], 16)).toByte()
    }
    return out
}
