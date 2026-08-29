package io.macula.cam2me.reachability

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.macula.sdk.FfiKeyPair
import kotlinx.coroutines.flow.first

private val Context.nodeKeyPairDataStore by preferencesDataStore(name = "node_key_pair")
private val SEED_KEY = stringPreferencesKey("seed_b64")

/**
 * This device's Ed25519 keypair, stable across restarts, used to
 * authenticate its macula mesh sessions -- its public key IS this
 * device's node_id on the wire. [FfiKeyPair.generate] mints a fresh,
 * puzzle-hardened keypair only once, ever, on first launch; every later
 * launch reconstructs the SAME keypair from its persisted seed via
 * [FfiKeyPair.fromSeedBytes]. Without this, node_id would change on
 * every restart and no contact could ever reach this device twice.
 *
 * This is a transport-layer signing key, nothing more -- no name, no
 * phone number, no credential, nothing biometric. It has no relationship
 * to a macula-passport subject (a bare UUID identifying a local dossier,
 * no keypair involved at all): a device's own node identity and a
 * person's identity are unrelated concepts that happen to share an
 * overloaded English word.
 */
object NodeKeyPair {
    suspend fun loadOrCreate(context: Context): FfiKeyPair {
        val seedB64 = context.nodeKeyPairDataStore.data.first()[SEED_KEY]
        if (seedB64 != null) {
            return FfiKeyPair.fromSeedBytes(Base64.decode(seedB64, Base64.NO_WRAP))
        }
        val keyPair = FfiKeyPair.generate()
        context.nodeKeyPairDataStore.edit {
            it[SEED_KEY] = Base64.encodeToString(keyPair.privateBytes(), Base64.NO_WRAP)
        }
        return keyPair
    }
}
