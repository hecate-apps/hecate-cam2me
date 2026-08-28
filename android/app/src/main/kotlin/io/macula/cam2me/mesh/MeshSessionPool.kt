package io.macula.cam2me.mesh

import io.macula.cam2me.data.KnownStation
import io.macula.sdk.FfiKeyPair
import io.macula.sdk.FfiSession
import io.macula.sdk.FfiTrust

/** One currently-open mesh connection, paired with the display metadata
 * (city/country) for whichever station it was most recently asked to
 * represent. */
data class ActiveStation(val target: KnownStation, val session: FfiSession)

/**
 * Up to 3 concurrent mesh connections -- redundancy, not load-balancing:
 * this device stays reachable via any one of them if another drops or the
 * network changes underneath it, and every [PresenceHeartbeat] instance
 * (one per session) advertises the full set, so a contact who only
 * overhears one heartbeat still learns every viable dial address.
 *
 * Keyed internally by (host, port), not the whole [KnownStation]: display
 * metadata (city/country) is free to change between discovery calls for
 * the same physical station without that being treated as "a different
 * station" and triggering an unnecessary reconnect.
 *
 * [connectAll] diffs the wanted target set against what's already open:
 * a station no longer wanted gets closed, one already open is left
 * untouched (reopening a QUIC handshake unnecessarily would defeat the
 * whole point of holding these long-lived), and only genuinely new
 * targets get a fresh [FfiSession.connect].
 *
 * [FfiTrust.WebPki]: standard CA-bundle validation, matching what the
 * public macula-demo station fleet presents. A self-hosted station
 * outside that fleet would need [FfiTrust.Pinned] instead -- not needed
 * for this app's known-stations list or hecate_stations' own discovery
 * results, which only ever return fleet members.
 */
object MeshSessionPool {
    @Volatile
    private var active: Map<Pair<String, Int>, ActiveStation> = emptyMap()

    suspend fun connectAll(targets: List<KnownStation>, identity: FfiKeyPair): List<ActiveStation> {
        val current = active
        val wantedKeys = targets.map { it.host to it.port }.toSet()

        current.filterKeys { it !in wantedKeys }.values.forEach { it.session.close(identity) }

        val next = targets.associateBy({ it.host to it.port }) { target ->
            val key = target.host to target.port
            current[key]?.let { ActiveStation(target, it.session) }
                ?: ActiveStation(
                    target,
                    FfiSession.connect(target.host, target.port.toUShort(), FfiTrust.WebPki, identity),
                )
        }
        active = next
        return next.values.toList()
    }

    fun current(): List<ActiveStation> = active.values.toList()
}
