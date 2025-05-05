package com.zhenbang.otw.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SubTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTask)

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    @Query("SELECT * FROM subTask")
    fun getAllSubTasks(): Flow<List<SubTask>>

    @Query("SELECT * FROM subTask WHERE taskId = :taskId")
    fun getSubTasksByTaskId(taskId: Int): Flow<List<SubTask>>

    @Query("SELECT * FROM subTask WHERE subTaskId = :subTaskId")
    fun getSubTaskById(subTaskId: Int): Flow<SubTask?>

    @Query("DELETE FROM subTask WHERE taskId = :taskId")
    suspend fun deleteSubTasksByTaskId(taskId: Int)

    @Query("DELETE FROM subTask")
    suspend fun deleteAllSubTasks()
}