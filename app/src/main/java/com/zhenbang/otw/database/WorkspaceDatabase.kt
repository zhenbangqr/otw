package com.zhenbang.otw.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// 1. Add Issue::class to entities and increment version to 4
@Database(entities = [Department::class, Task::class, Issue::class], version = 4, exportSchema = false)
abstract class WorkspaceDatabase : RoomDatabase() {
    abstract fun departmentDao(): DepartmentDao
    abstract fun taskDao(): TaskDao
    abstract fun issueDao(): IssueDao // 2. Add abstract fun for IssueDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE departments ADD COLUMN imageUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Make sure the default value handling matches the entity's default
                database.execSQL("ALTER TABLE tasks ADD COLUMN creationTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 3. Add Migration from version 3 to 4
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new 'issues' table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `issues` (
                        `issueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `issueTitle` TEXT NOT NULL,
                        `issueDescription` TEXT NOT NULL,
                        `departmentId` INTEGER NOT NULL,
                        `creationTimestamp` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`departmentId`) REFERENCES `departments`(`departmentId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                // Add index on departmentId for foreign key performance
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_departmentId` ON `issues` (`departmentId`)")
            }
        }

        @Volatile
        private var INSTANCE: WorkspaceDatabase? = null

        fun getDatabase(context: android.content.Context): WorkspaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    WorkspaceDatabase::class.java,
                    "workspace_database"
                )
                    // 4. Add the new migration
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}