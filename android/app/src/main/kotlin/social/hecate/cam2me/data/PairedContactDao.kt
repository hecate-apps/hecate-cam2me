package social.hecate.cam2me.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PairedContactDao {
    @Query("SELECT * FROM paired_contacts ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<PairedContact>>

    @Query("SELECT * FROM paired_contacts WHERE nodeIdHex = :nodeIdHex")
    suspend fun findByNodeId(nodeIdHex: String): PairedContact?

    /** Pairing: insert a brand-new contact, or overwrite one re-paired
     * (e.g. after they reinstalled and their display name changed). */
    @Upsert
    suspend fun upsert(contact: PairedContact)

    /** A presence heartbeat matched this contact -- refresh where
     * they're online, without touching [PairedContact.displayName] or
     * [PairedContact.pairedAtMs]. No-ops (0 rows affected) if the
     * contact was deleted between the heartbeat's match check and this
     * call, which is fine -- there's nothing to update.
     */
    @Query(
        """
        UPDATE paired_contacts
        SET lastSeenOnlineAtMs = :seenAtMs,
            lastKnownStationHost = :host,
            lastKnownStationPort = :port
        WHERE nodeIdHex = :nodeIdHex
        """
    )
    suspend fun recordPresence(nodeIdHex: String, seenAtMs: Long, host: String, port: Int)

    @Query("DELETE FROM paired_contacts WHERE nodeIdHex = :nodeIdHex")
    suspend fun delete(nodeIdHex: String)
}
