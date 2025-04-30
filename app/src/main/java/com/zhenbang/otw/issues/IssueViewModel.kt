package com.zhenbang.otw.issues

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.database.Issue // Import Issue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class IssueViewModel(private val repository: IssueRepository) : ViewModel() {

    // Get issues for a specific department
    fun getIssuesByDepartmentId(departmentId: Int): Flow<List<Issue>> {
        return repository.getIssuesByDepartmentId(departmentId)
    }

    // Get a specific issue by its ID
    fun getIssueById(issueId: Int): Flow<Issue?> {
        return repository.getIssueById(issueId)
    }

    /**
     * Inserts a new issue or updates an existing one based on issueId.
     * Mirrors the TaskViewModel's insertTask which uses OnConflictStrategy.REPLACE in the DAO.
     */
    fun upsertIssue(issue: Issue) = viewModelScope.launch {
        val issueToSave = if (issue.issueId == 0) {
            issue.copy(
                issueTitle = issue.issueTitle.trim(),
                issueDescription = issue.issueDescription.trim(),
                creationTimestamp = System.currentTimeMillis()
            )
        } else {
            issue.copy(
                issueTitle = issue.issueTitle.trim(),
                issueDescription = issue.issueDescription.trim()
            )
        }
        repository.insertIssue(issueToSave)
    }

    // Delete an issue
    fun deleteIssue(issue: Issue) = viewModelScope.launch {
        repository.deleteIssue(issue)
    }

    // Factory for creating the ViewModel with dependencies
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IssueViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return IssueViewModel(IssueRepository.getRepository(context.applicationContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}