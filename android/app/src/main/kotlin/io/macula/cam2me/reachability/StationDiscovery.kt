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
        // hostname preferred over host_advertised: MeshSessionPool connects
        // with FfiTrust.WebPki, which validates a CA cert against the
        // hostname used to connect -- station-de-frankfurt.macula.io's cert
        // has no SAN for its own bare IP, so dialing host_advertised (an IP
        // literal, e.g. "2a01:7e01::f03c:94ff:fe22:719e") under WebPki would
        // fail TLS validation outright. Confirmed against a real
        // hecate_stations.list_stations response, not assumed -- see
        // FfiTrust's own doc: Pinned{node_id} is "the right mode once a
        // station's identity is known (DHT-resolved...)", i.e. exactly the
        // host_advertised case, which this doesn't do yet. Every station on
        // the live fleet publishes both fields together, so falling back to
        // host_advertised here doesn't currently happen in practice --
        // wiring Pinned trust into MeshSessionPool is real, separate,
        // not-yet-built work for the day a station-endpoint-only entry
        // (no node_record, no hostname) actually shows up.
        val host = field("hostname")?.asText()
            ?: field("host_advertised")?.asItems()?.firstOrNull()?.asText()
            ?: return null
        return KnownStation(host, port, city, country)
    }
}
