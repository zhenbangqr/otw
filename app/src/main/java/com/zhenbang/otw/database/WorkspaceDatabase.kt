package com.zhenbang.otw.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Department::class, Task::class, Issue::class, DeptUser::class, TaskAssignment::class, SubTask::class],
    version = 10,
    exportSchema = false
)
abstract class WorkspaceDatabase : RoomDatabase() {
    abstract fun departmentDao(): DepartmentDao
    abstract fun taskDao(): TaskDao
    abstract fun issueDao(): IssueDao
    abstract fun deptUserDao(): DeptUserDao
    abstract fun taskAssignmentDao(): TaskAssignmentDao
    abstract fun subTaskDao(): SubTaskDao

    companion object {

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE departments ADD COLUMN imageUrl TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN creationTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `issues` (
                        `issueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `issueTitle` TEXT NOT NULL,
                        `issueDescription` TEXT NOT NULL,
                        `departmentId` INTEGER NOT NULL,
                        `creationTimestamp` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`departmentId`) REFERENCES `departments`(`departmentId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_issues_departmentId` ON `issues` (`departmentId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE departments ADD COLUMN creatorEmail TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deptusers` (
                        `creationTimestamp` INTEGER NOT NULL,
                        `userEmail` TEXT NOT NULL,
                        `deptUserId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `departmentId` INTEGER NOT NULL,
                        FOREIGN KEY(`departmentId`) REFERENCES `departments`(`departmentId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        UNIQUE(`userEmail`, `departmentId`)
                    )
                """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deptusers_userEmail_departmentId` ON `deptusers` (`userEmail`, `departmentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deptusers_departmentId` ON `deptusers` (`departmentId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN creatorEmail TEXT")
                database.execSQL("ALTER TABLE issues ADD COLUMN creatorEmail TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS deptusers");
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deptUsers` (
                        `creationTimestamp` INTEGER NOT NULL,
                        `userEmail` TEXT NOT NULL,
                        `deptUserId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `departmentId` INTEGER NOT NULL,
                        FOREIGN KEY(`departmentId`) REFERENCES `departments`(`departmentId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        UNIQUE(`userEmail`, `departmentId`)
                    )
                """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_deptUsers_userEmail_departmentId` ON `deptUsers` (`userEmail`, `departmentId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deptUsers_departmentId` ON `deptUsers` (`departmentId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `taskAssignment` (
                        `taskAssignmentId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `userEmail` TEXT NOT NULL,
                        `creationTimeStamp` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`taskId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_taskAssignment_taskId` ON `taskAssignment` (`taskId`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `subTask` (
                        `subTaskId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subTaskTitle` TEXT NOT NULL,
                        `subTaskDesc` TEXT NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `creationTimeStamp` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`taskId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_subTask_taskId` ON `subTask` (`taskId`)")
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}