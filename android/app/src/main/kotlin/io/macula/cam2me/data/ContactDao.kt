package io.macula.cam2me.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE NOT blocked ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<Contact>>

    /** A one-shot snapshot for matching an incoming presence heartbeat's
     * phone-number hash against every known contact -- SQLite has no
     * SHA-256, so that comparison happens in Kotlin, over this list,
     * rather than as a WHERE clause. */
    @Query("SELECT * FROM contacts WHERE NOT blocked")
    suspend fun listAll(): List<Contact>

    @Query("SELECT * FROM contacts WHERE phoneNumber = :phoneNumber")
    suspend fun findByPhoneNumber(phoneNumber: String): Contact?

    /** Adding a contact, or renaming one already added -- the only
     * "pairing" step this app has. */
    @Upsert
    suspend fun upsert(contact: Contact)

    /** Stops matching this number against incoming presence heartbeats
     * and hides it from the dial list -- the app's answer to unwanted
     * calls, alongside just denying the ring itself. */
    @Query("UPDATE contacts SET blocked = 1 WHERE phoneNumber = :phoneNumber")
    suspend fun block(phoneNumber: String)

    @Query("DELETE FROM contacts WHERE phoneNumber = :phoneNumber")
    suspend fun delete(phoneNumber: String)
}
