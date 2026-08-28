package io.macula.cam2me.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Contact::class, PhonePresence::class], version = 2, exportSchema = true)
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
                    // v1 -> v2: phone_presence's single stationHost/stationPort
                    // became stationsJson (a device can now be reachable via up
                    // to 3 stations at once -- see MeshSessionPool). No release
                    // has ever shipped v1, so a real migration would just be
                    // dead code; destructive is the honest choice while this
                    // schema is still forming.
                    .fallbackToDestructiveMigration(true)
                    .build().also { instance = it }
            }
    }
}
