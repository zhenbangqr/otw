package com.zhenbang.otw.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SummaryHistoryEntity::class], version = 1, exportSchema = false) // Increment version on schema changes
abstract class AppDatabase : RoomDatabase() {

    abstract fun summaryHistoryDao(): SummaryHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "otw_app_database" // Database file name
                )
                    // Add migrations here if needed in the future
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}