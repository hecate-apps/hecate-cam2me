package io.macula.cam2me.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

private val MY_PHONE_NUMBER = stringPreferencesKey("my_phone_number")
private val AUTO_DISCOVERY = booleanPreferencesKey("auto_discovery")
private val STATION_HOST = stringPreferencesKey("station_host")
private val STATION_PORT = intPreferencesKey("station_port")

/**
 * The handful of scalars this device needs to join the mesh and be
 * discoverable: this device owner's own phone number (broadcast, hashed,
 * on the presence heartbeat -- see PresenceHeartbeat), whether to
 * auto-connect to the nearest stations via GPS (the default -- see
 * StationDiscovery), and a manual station to fall back to when auto
 * discovery is off or the user has explicitly picked one. The fallback
 * defaults to the same public door the macula-demo fleet's other
 * public-facing services already use.
 */
object SettingsStore {
    private const val DEFAULT_STATION_HOST = "station-de-frankfurt.macula.io"
    private const val DEFAULT_STATION_PORT = 4433

    data class Settings(
        val myPhoneNumber: String?,
        val autoDiscovery: Boolean,
        val stationHost: String,
        val stationPort: Int,
    )

    fun observe(context: Context): Flow<Settings> =
        context.settingsDataStore.data.map { prefs ->
            Settings(
                myPhoneNumber = prefs[MY_PHONE_NUMBER],
                autoDiscovery = prefs[AUTO_DISCOVERY] ?: true,
                stationHost = prefs[STATION_HOST] ?: DEFAULT_STATION_HOST,
                stationPort = prefs[STATION_PORT] ?: DEFAULT_STATION_PORT,
            )
        }

    suspend fun setMyPhoneNumber(context: Context, phoneNumber: String) {
        context.settingsDataStore.edit { it[MY_PHONE_NUMBER] = phoneNumber }
    }

    suspend fun setAutoDiscovery(context: Context) {
        context.settingsDataStore.edit { it[AUTO_DISCOVERY] = true }
    }

    /** Picking a specific station is an explicit override -- it turns
     * auto discovery off until [setAutoDiscovery] is chosen again. */
    suspend fun setManualStation(context: Context, host: String, port: Int) {
        context.settingsDataStore.edit {
            it[AUTO_DISCOVERY] = false
            it[STATION_HOST] = host
            it[STATION_PORT] = port
        }
    }
}
