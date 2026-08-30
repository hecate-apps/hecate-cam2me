package io.macula.cam2me.reachability

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.macula.sdk.FfiException
import io.macula.sdk.FfiKeyPair
import kotlinx.coroutines.flow.first

private val Context.nodeKeyPairDataStore by preferencesDataStore(name = "node_key_pair")
private val SEED_KEY = stringPreferencesKey("seed_b64")

// Address this device's credential the same way every `keyring`
// consumer does -- scoped to this app, not sandboxed to the SDK.
private const val KEYSTORE_SERVICE = "io.macula.cam2me"
private const val KEYSTORE_ACCOUNT = "node_key_pair"

/**
 * This device's Ed25519 keypair, stable across restarts, used to
 * authenticate its macula mesh sessions -- its public key IS this
 * device's node_id on the wire. [FfiKeyPair.generate] mints a fresh,
 * puzzle-hardened keypair only once, ever, on first launch; every later
 * launch reconstructs the SAME keypair from its persisted seed via
 * [FfiKeyPair.fromSeedBytes]. Without this, node_id would change on
 * every restart and no contact could ever reach this device twice.
 *
 * Persisted via [FfiKeyPair.saveToKeystore]/[FfiKeyPair.loadFromKeystore]
 * (Android Keystore-backed, `macula-rust-sdk`'s `KeyStore` mechanism) --
 * NOT plain DataStore, which is where this raw Ed25519 seed used to live
 * unencrypted. Requires [io.crates.keyring.Keyring.initializeNdkContext]
 * to have already run (see [io.macula.cam2me.MainActivity.onCreate]).
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
        try {
            return FfiKeyPair.loadFromKeystore(KEYSTORE_SERVICE, KEYSTORE_ACCOUNT)
        } catch (_: FfiException.KeystoreNotFound) {
            // Nothing in the keystore yet -- either a fresh install, or
            // an install from before this migration that still has its
            // seed in the old plain DataStore. Recover that one instead
            // of silently minting a new node_id (and orphaning every
            // contact that already knows this device) if it's there.
        }

        val migrated = migrateFromLegacyDataStore(context)
        if (migrated != null) {
            migrated.saveToKeystore(KEYSTORE_SERVICE, KEYSTORE_ACCOUNT)
            return migrated
        }

        val keyPair = FfiKeyPair.generate()
        keyPair.saveToKeystore(KEYSTORE_SERVICE, KEYSTORE_ACCOUNT)
        return keyPair
    }

    /**
     * One-time recovery of a seed persisted the old way (plain, unencrypted
     * DataStore) before the keystore migration existed. Returns null if
     * nothing is there -- the normal case for any install that either
     * started fresh after this migration, or has already completed it
     * (the DataStore value is cleared once migrated, so this path is only
     * ever taken once per install).
     */
    private suspend fun migrateFromLegacyDataStore(context: Context): FfiKeyPair? {
        val seedB64 = context.nodeKeyPairDataStore.data.first()[SEED_KEY] ?: return null
        val keyPair = FfiKeyPair.fromSeedBytes(Base64.decode(seedB64, Base64.NO_WRAP))
        context.nodeKeyPairDataStore.edit { it.remove(SEED_KEY) }
        return keyPair
    }
}
