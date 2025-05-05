package com.zhenbang.otw.issues

import android.content.Context
import com.zhenbang.otw.database.Issue
import com.zhenbang.otw.database.IssueDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IssueRepository(private val issueDao: IssueDao) {

    fun getIssuesByDepartmentId(departmentId: Int): Flow<List<Issue>> {
        return issueDao.getIssuesByDepartmentId(departmentId)
    }

    fun getIssueById(issueId: Int): Flow<Issue?> {
        return issueDao.getIssueById(issueId)
    }

    suspend fun insertIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.insertIssue(issue)
        }
    }

    suspend fun updateIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.updateIssue(issue)
        }
    }

    suspend fun deleteIssue(issue: Issue) {
        withContext(Dispatchers.IO) {
            issueDao.deleteIssue(issue)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: IssueRepository? = null

        fun getRepository(context: Context): IssueRepository {
            return INSTANCE ?: synchronized(this) {
                val database = WorkspaceDatabase.getDatabase(context)
                val instance = IssueRepository(database.issueDao())
                INSTANCE = instance
                instance
            }
        }
    }
}