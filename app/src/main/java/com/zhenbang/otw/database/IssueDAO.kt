package com.zhenbang.otw.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIssue(issue: Issue)

    @Update
    suspend fun updateIssue(issue: Issue)

    @Delete
    suspend fun deleteIssue(issue: Issue)

    @Query("SELECT * FROM issues WHERE issueId = :issueId")
    fun getIssueById(issueId: Int): Flow<Issue?>

    @Query("SELECT * FROM issues WHERE departmentId = :departmentId ORDER BY creationTimestamp DESC")
    fun getIssuesByDepartmentId(departmentId: Int): Flow<List<Issue>>

    @Query("SELECT * FROM issues ORDER BY creationTimestamp DESC")
    fun getAllIssues(): Flow<List<Issue>>
}