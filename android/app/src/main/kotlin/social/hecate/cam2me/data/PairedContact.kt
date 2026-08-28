package social.hecate.cam2me.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A contact this device has paired with -- learned once via QR exchange,
 * kept fresh afterward via presence heartbeats (see [PairedContactDao]).
 *
 * [nodeIdHex] is the primary key, stored as lowercase hex rather than
 * the raw [ByteArray] the FFI layer hands back: `ByteArray` has no
 * content-based `equals`/`hashCode` in Kotlin (two arrays holding the
 * same bytes compare unequal by reference unless explicitly overridden
 * with `contentEquals`), which would silently break Room's own entity
 * diffing. Hex sidesteps that outright, and matches how node_id is
 * already displayed elsewhere in this app.
 */
@Entity(tableName = "paired_contacts")
data class PairedContact(
    @PrimaryKey val nodeIdHex: String,
    val displayName: String,
    val pairedAtMs: Long,
    /** From the pairing QR at first; refreshed by presence heartbeats
     * afterward -- see the module doc on `PresenceHeartbeat` once that
     * lands. Null only if a heartbeat has genuinely never been observed
     * for this contact since pairing. */
    val lastKnownStationHost: String?,
    val lastKnownStationPort: Int?,
    val lastSeenOnlineAtMs: Long?,
)
