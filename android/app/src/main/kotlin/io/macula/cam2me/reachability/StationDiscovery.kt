package io.macula.cam2me.reachability

import io.macula.cam2me.APP_REALM
import io.macula.sdk.FfiCallResponse
import io.macula.sdk.FfiKeyPair
import io.macula.sdk.FfiSession
import io.macula.sdk.FfiValue

private const val LIST_STATIONS_PROCEDURE = "hecate_stations.list_stations"
private const val CALL_TIMEOUT_MS = 8_000UL
private const val DEFAULT_QUIC_PORT = 4433

/**
 * Finds the nearest macula stations to a device location by calling
 * `hecate_stations.list_stations` over an already-connected bootstrap
 * session -- the RPC is mesh-wide (any connected station can route a CALL
 * to any advertised provider), so the bootstrap station never needs to be
 * one of the results itself.
 */
object StationDiscovery {
    /**
     * Returns null on ANY failure (timeout, RPC error, an unparseable
     * response) rather than throwing -- the caller's job is to fall back
     * to the static station list, not to surface this as an app error. A
     * mobile network is exactly the environment where this call sometimes
     * just doesn't complete.
     */
    suspend fun nearestStations(
        bootstrap: FfiSession,
        identity: FfiKeyPair,
        lat: Double,
        lng: Double,
        limit: Int,
    ): List<KnownStation>? {
        val payload = ffiFields(
            "near" to ffiFields(
                "lat" to FfiValue.Float(lat),
                "lng" to FfiValue.Float(lng),
                "limit" to FfiValue.Int(limit.toLong()),
            ),
        )
        val response = try {
            bootstrap.call(LIST_STATIONS_PROCEDURE, APP_REALM, payload, CALL_TIMEOUT_MS, identity)
        } catch (e: Exception) {
            return null
        }
        val result = response as? FfiCallResponse.Result ?: return null
        val stations = result.payload.field("stations")?.asItems() ?: return null
        return stations.mapNotNull { it.toKnownStation() }.take(limit)
    }

    private fun FfiValue.toKnownStation(): KnownStation? {
        val city = field("city")?.asText() ?: ""
        val country = field("country")?.asText() ?: ""
        val port = field("quic_port")?.asInt()?.toInt() ?: DEFAULT_QUIC_PORT
        // host_advertised+node_id (Pinned trust) preferred over hostname
        // (WebPki): host_advertised is the station's real dial address --
        // hostname is a convenience for the subset of stations that also
        // sit behind DNS+CA cert. A no-DNS station (stations-linode-toronto,
        // 2026-08-29, provisioned specifically to test this) publishes
        // host_advertised with no hostname at all; MeshSessionPool picks
        // FfiTrust.Pinned(nodeId) whenever nodeId is non-null, which is the
        // only mode able to validate a connection with no CA-issued cert
        // to check -- see FfiTrust's own doc. Falling back to hostname
        // (WebPki) covers the case where a station only published a
        // node_record and no station_endpoint at all.
        val pinned = field("node_id")?.asBytes()?.let { nodeId ->
            field("host_advertised")?.asItems()?.firstOrNull()?.asText()?.let { it to nodeId }
        }
        val (host, nodeId) = pinned ?: ((field("hostname")?.asText() ?: return null) to null)
        return KnownStation(host, port, city, country, nodeId)
    }
}
