package io.macula.cam2me.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Where a phone number was last seen on the mesh. Filled in by matching
 * incoming `cam2me.presence` heartbeats -- payload carries a hash of the
 * sender's own phone number plus their current node_id and station --
 * against [Contact.phoneNumber]. Never written for a number that isn't a
 * [Contact]; there's nothing to do with presence for a number you
 * haven't added.
 */
@Entity(tableName = "phone_presence")
data class PhonePresence(
    @PrimaryKey val phoneNumber: String,
    val nodeIdHex: String,
    val stationHost: String,
    val stationPort: Int,
    val lastSeenOnlineAtMs: Long,
)
