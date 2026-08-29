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
        // host_advertised (the literal dial address the station announced
        // for itself) is preferred over hostname (the node's own
        // self-reported name, not guaranteed to resolve for every
        // client) -- see station_read_model.erl's own doc for why the two
        // fields exist independently.
        val host = field("host_advertised")?.asItems()?.firstOrNull()?.asText()
            ?: field("hostname")?.asText()
            ?: return null
        return KnownStation(host, port, city, country)
    }
}
