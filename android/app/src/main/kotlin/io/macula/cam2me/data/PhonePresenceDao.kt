package io.macula.cam2me.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PhonePresenceDao {
    @Query("SELECT * FROM phone_presence WHERE phoneNumber = :phoneNumber")
    suspend fun find(phoneNumber: String): PhonePresence?

    @Query(
        """
        SELECT phone_presence.* FROM phone_presence
        JOIN contacts ON contacts.phoneNumber = phone_presence.phoneNumber
        WHERE NOT contacts.blocked
        """
    )
    fun observeForContacts(): Flow<List<PhonePresence>>

    /** A presence heartbeat's phone-number hash matched a known contact --
     * record where they currently are. */
    @Upsert
    suspend fun upsert(presence: PhonePresence)
}
