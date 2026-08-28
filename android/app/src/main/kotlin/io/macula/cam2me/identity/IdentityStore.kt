package io.macula.cam2me.identity

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.macula.sdk.FfiKeyPair
import kotlinx.coroutines.flow.first

private val Context.identityDataStore by preferencesDataStore(name = "identity")
private val SEED_KEY = stringPreferencesKey("seed_b64")

/**
 * This device's mesh identity, stable across restarts. [FfiKeyPair.generate]
 * mints a fresh, puzzle-hardened keypair only once, ever, on first launch;
 * every later launch reconstructs the SAME keypair from its persisted seed
 * via [FfiKeyPair.fromSeedBytes]. Without this, node_id would change on
 * every restart and no contact could ever reach this device twice.
 */
object IdentityStore {
    suspend fun loadOrCreate(context: Context): FfiKeyPair {
        val seedB64 = context.identityDataStore.data.first()[SEED_KEY]
        if (seedB64 != null) {
            return FfiKeyPair.fromSeedBytes(Base64.decode(seedB64, Base64.NO_WRAP))
        }
        val identity = FfiKeyPair.generate()
        context.identityDataStore.edit {
            it[SEED_KEY] = Base64.encodeToString(identity.privateBytes(), Base64.NO_WRAP)
        }
        return identity
    }
}
