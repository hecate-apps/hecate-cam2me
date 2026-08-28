package social.hecate.cam2me.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A phone number this device's owner has chosen to add -- the whole of
 * "pairing" is typing a number and giving it a name, nothing more.
 * Where that number currently is on the mesh is tracked separately in
 * [PhonePresence], filled in once a presence heartbeat matches it.
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey val phoneNumber: String,
    val displayName: String,
    val addedAtMs: Long,
    val blocked: Boolean = false,
)
