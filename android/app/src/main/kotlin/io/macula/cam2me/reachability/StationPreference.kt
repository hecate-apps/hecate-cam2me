package io.macula.cam2me.reachability

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stationPreferenceDataStore by preferencesDataStore(name = "station_preference")

private val AUTO_DISCOVERY = booleanPreferencesKey("auto_discovery")
private val STATION_HOST = stringPreferencesKey("station_host")
private val STATION_PORT = intPreferencesKey("station_port")

/**
 * How this device picks which station(s) to connect to: auto-discover
 * the nearest via GPS (the default -- see [StationDiscovery]), or a
 * manually-picked fallback station used either while auto discovery is
 * off, or as the bootstrap connection auto discovery itself needs before
 * it can call `hecate_stations.list_stations`. The fallback defaults to
 * the same public door the macula-demo fleet's other public-facing
 * services already use.
 */
object StationPreference {
    private const val DEFAULT_STATION_HOST = "station-de-frankfurt.macula.io"
    private const val DEFAULT_STATION_PORT = 4433

    data class Preference(
        val autoDiscovery: Boolean,
        val stationHost: String,
        val stationPort: Int,
    )

    fun observe(context: Context): Flow<Preference> =
        context.stationPreferenceDataStore.data.map { prefs ->
            Preference(
                autoDiscovery = prefs[AUTO_DISCOVERY] ?: true,
                stationHost = prefs[STATION_HOST] ?: DEFAULT_STATION_HOST,
                stationPort = prefs[STATION_PORT] ?: DEFAULT_STATION_PORT,
            )
        }

    suspend fun setAutoDiscovery(context: Context) {
        context.stationPreferenceDataStore.edit { it[AUTO_DISCOVERY] = true }
    }

    /** Picking a specific station is an explicit override -- it turns
     * auto discovery off until [setAutoDiscovery] is chosen again. */
    suspend fun setManualStation(context: Context, host: String, port: Int) {
        context.stationPreferenceDataStore.edit {
            it[AUTO_DISCOVERY] = false
            it[STATION_HOST] = host
            it[STATION_PORT] = port
        }
    }
}
