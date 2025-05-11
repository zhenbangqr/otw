package com.zhenbang.otw.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskAssignmentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskAssignment(taskAssignment: TaskAssignment)

    @Update
    suspend fun updateTaskAssignment(taskAssignment: TaskAssignment)

    @Delete
    suspend fun deleteTaskAssignment(taskAssignment: TaskAssignment)

    @Query("SELECT * FROM TaskAssignment WHERE taskId = :taskId")
    fun getTaskAssignmentsForTask(taskId: Int): Flow<List<TaskAssignment>>

    @Query("SELECT * FROM TaskAssignment WHERE userEmail = :userEmail")
    fun getTaskAssignmentsForUser(userEmail: String): Flow<List<TaskAssignment>>

    @Query("DELETE FROM TaskAssignment WHERE taskId = :taskId")
    suspend fun deleteTaskAssignmentsByTaskId(taskId: Int)
}