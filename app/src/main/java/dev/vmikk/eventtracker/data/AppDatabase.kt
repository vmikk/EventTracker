package dev.vmikk.eventtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        EventTypeEntity::class,
        DayEventEntity::class,
        CustomEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun dayEventDao(): DayEventDao
    abstract fun customEventDao(): CustomEventDao

    companion object {
        private const val DB_NAME = "eventtracker.db"

        fun databaseName(): String = DB_NAME

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                ).build().also { INSTANCE = it }
            }

        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}


