package com.zhenbang.otw.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.database.DeptUser
import com.zhenbang.otw.database.SubTask
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.database.TaskAssignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    fun insertTask(task: Task, userEmails: List<String>, subTasks: List<SubTask>) = viewModelScope.launch {
        repository.insertTask(task, userEmails, subTasks)
    }

    fun updateTask(task: Task, userEmails: List<String>, subTasks: List<SubTask>) = viewModelScope.launch {
        repository.updateTask(task, userEmails, subTasks)
    }

    fun updateTaskCompletion(task: Task, isCompleted: Boolean) = viewModelScope.launch {
        repository.updateTaskCompletion(task, isCompleted)
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        repository.deleteTask(task)
    }

    fun getTaskById(taskId: Int): Flow<Task?> {
        return repository.getTaskById(taskId)
    }

    fun getTasksByDepartmentId(departmentId: Int): Flow<List<Task>> {
        return repository.getTasksByDepartmentId(departmentId)
    }

    fun getDeptUsersByDepartmentId(departmentId: Int): Flow<List<DeptUser>> {
        return repository.getDeptUsersByDepartmentId(departmentId)
    }

    fun getTaskAssignmentsForUser(userEmail: String): Flow<List<TaskAssignment>> {
        return repository.getTaskAssignmentsForUser(userEmail)
    }

    fun getAssignedUsersForTask(taskId: Int): Flow<List<TaskAssignment>> {
        return repository.getAssignedUsersForTask(taskId)
    }

    fun canEditTask(taskId: Int, currentUserEmail: String?): Flow<Boolean> {
        return combine(
            getTaskById(taskId),
            getAssignedUsersForTask(taskId)
        ) { task, assignedUsers ->
            if (currentUserEmail == null || task == null) {
                false
            } else {
                task.creatorEmail == currentUserEmail || assignedUsers.any { it.userEmail == currentUserEmail }
            }
        }
    }


    fun updateSubTaskCompletion(subTask: SubTask, isCompleted: Boolean) = viewModelScope.launch {  // Added updateSubTask
        repository.updateSubTaskCompletion(subTask, isCompleted)
    }

    fun getSubTasksByTaskId(taskId: Int): Flow<List<SubTask>> {
        return repository.getSubTasksByTaskId(taskId)
    }

    fun getSubTaskById(subTaskId: Int): Flow<SubTask?> {
        return repository.getSubTaskById(subTaskId)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TaskViewModel(TaskRepository.getRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}