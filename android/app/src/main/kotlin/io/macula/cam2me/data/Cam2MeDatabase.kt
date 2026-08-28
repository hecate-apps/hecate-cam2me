package io.macula.cam2me.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                ).build().also { instance = it }
            }
    }
}
