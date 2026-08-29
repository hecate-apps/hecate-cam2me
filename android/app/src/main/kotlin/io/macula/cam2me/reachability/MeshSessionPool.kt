package io.macula.cam2me.reachability

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
 * Keyed internally by [KnownStation.nodeId] when a target has one, else by
 * (host, port): a discovered station's identity (its keypair) is what stays
 * constant, not its IP -- host_advertised can shift under DHCP without that
 * being treated as "a different station" and triggering an unnecessary
 * reconnect. The static [KNOWN_STATIONS] list has no nodeId, so it keeps the
 * old (host, port) keying, under which display metadata (city/country) is
 * likewise free to change between discovery calls for the same physical
 * station.
 *
 * [connectAll] diffs the wanted target set against what's already open:
 * a station no longer wanted gets closed, one already open is left
 * untouched (reopening a QUIC handshake unnecessarily would defeat the
 * whole point of holding these long-lived), and only genuinely new
 * targets get a fresh [FfiSession.connect].
 *
 * Trust follows [KnownStation.nodeId]: present means dialing a bare
 * `host_advertised` literal with no CA cert to validate, so
 * [FfiTrust.Pinned] is the only mode that can authenticate the connection
 * at all. Absent (the static list, and any discovery result that only ever
 * supplied a hostname) means [FfiTrust.WebPki], standard CA-bundle
 * validation against that hostname.
 */
object MeshSessionPool {
    @Volatile
    private var active: Map<Any, ActiveStation> = emptyMap()

    private fun KnownStation.poolKey(): Any =
        nodeId?.joinToString("") { "%02x".format(it) } ?: (host to port)

    private fun KnownStation.trust(): FfiTrust =
        nodeId?.let { FfiTrust.Pinned(it) } ?: FfiTrust.WebPki

    suspend fun connectAll(targets: List<KnownStation>, identity: FfiKeyPair): List<ActiveStation> {
        val current = active
        val wantedKeys = targets.map { it.poolKey() }.toSet()

        current.filterKeys { it !in wantedKeys }.values.forEach { it.session.close(identity) }

        val next = targets.associateBy({ it.poolKey() }) { target ->
            current[target.poolKey()]?.let { ActiveStation(target, it.session) }
                ?: ActiveStation(
                    target,
                    FfiSession.connect(target.host, target.port.toUShort(), target.trust(), identity),
                )
        }
        active = next
        return next.values.toList()
    }

    fun current(): List<ActiveStation> = active.values.toList()
}
