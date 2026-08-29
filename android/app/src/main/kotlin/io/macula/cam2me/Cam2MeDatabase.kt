package io.macula.cam2me

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.macula.cam2me.contacts.Contact
import io.macula.cam2me.contacts.ContactDao
import io.macula.cam2me.presence.PhonePresence
import io.macula.cam2me.presence.PhonePresenceDao

/**
 * The one on-device database, shared by whichever slices need local
 * persistence ([io.macula.cam2me.contacts], [io.macula.cam2me.presence])
 * -- this class itself owns no domain logic, just the wiring Room
 * requires to live at a single top level regardless of how many slices
 * feed into it.
 */
@Database(entities = [Contact::class, PhonePresence::class], version = 1, exportSchema = true)
abstract class Cam2MeDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun phonePresenceDao(): PhonePresenceDao

    companion object {
        @Volatile
        private var instance: Cam2MeDatabase? = null

        fun getInstance(context: Context): Cam2MeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    Cam2MeDatabase::class.java,
                    "cam2me.db",
                )
                    // No release has ever shipped this schema in any shape,
                    // so a real migration would just be dead code --
                    // destructive is the honest choice while it's still
                    // forming.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}
