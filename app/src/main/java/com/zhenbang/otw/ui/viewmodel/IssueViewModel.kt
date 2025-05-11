package com.zhenbang.otw.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.data.local.Issue
import com.zhenbang.otw.data.repository.IssueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class IssueViewModel(private val repository: IssueRepository) : ViewModel() {

    fun getIssuesByDepartmentId(departmentId: Int): Flow<List<Issue>> {
        return repository.getIssuesByDepartmentId(departmentId)
    }

    fun getIssueById(issueId: Int): Flow<Issue?> {
        return repository.getIssueById(issueId)
    }

    fun upsertIssue(issue: Issue) = viewModelScope.launch {
        val issueToSave = if (issue.issueId == 0) {
            issue.copy(
                issueTitle = issue.issueTitle.trim(),
                issueDescription = issue.issueDescription.trim(),
                creationTimestamp = System.currentTimeMillis(),
                creatorEmail = issue.creatorEmail
            )
        } else {
            issue.copy(
                issueTitle = issue.issueTitle.trim(),
                issueDescription = issue.issueDescription.trim()
            )
        }
        repository.insertIssue(issueToSave)
    }

    fun deleteIssue(issue: Issue) = viewModelScope.launch {
        repository.deleteIssue(issue)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(IssueViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return IssueViewModel(IssueRepository.Companion.getRepository(context.applicationContext)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}