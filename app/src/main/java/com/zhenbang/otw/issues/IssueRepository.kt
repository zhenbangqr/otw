package com.zhenbang.otw.issues

import android.content.Context
import com.zhenbang.otw.database.Issue
import com.zhenbang.otw.database.IssueDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IssueRepository(private val issueDao: IssueDao) {

    // Function to get issues for a specific department
    fun getIssuesByDepartmentId(departmentId: Int): Flow<List<Issue>> {
        return issueDao.getIssuesByDepartmentId(departmentId)
    }

    // Function to get a single issue by its ID
    fun getIssueById(issueId: Int): Flow<Issue?> {
        return issueDao.getIssueById(issueId)
    }

    // Suspend function to insert a new issue (runs on IO thread)
    suspend fun insertIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.insertIssue(issue)
        }
    }

    // Suspend function to update an existing issue (runs on IO thread)
    suspend fun updateIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.updateIssue(issue)
        }
    }

    // Suspend function to delete an issue (runs on IO thread)
    suspend fun deleteIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.deleteIssue(issue)
        }
    }

    // Companion object for singleton pattern
    companion object {
        @Volatile
        private var INSTANCE: IssueRepository? = null

        fun getRepository(context: Context): IssueRepository {
            return INSTANCE ?: synchronized(this) {
                // Get database instance and then the DAO
                val database = WorkspaceDatabase.getDatabase(context)
                val instance = IssueRepository(database.issueDao()) // Use issueDao() here
                INSTANCE = instance
                instance
            }
        }
    }
}