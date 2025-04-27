package com.zhenbang.otw.tasks

import android.content.Context
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.database.TaskDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TaskRepository(private val taskDao: TaskDao) {

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.insertTask(task)
        }
    }

    suspend fun updateTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.updateTask(task)
        }
    }

    suspend fun deleteTask(task: Task) {
        withContext(Dispatchers.IO) {
            taskDao.deleteTask(task)
        }
    }

    fun getTaskById(taskId: Int): Flow<Task?> {
        return taskDao.getTaskById(taskId)
    }

    fun getTasksByDepartmentId(departmentId: Int): Flow<List<Task>> {
        return taskDao.getTasksByDepartmentId(departmentId)
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskRepository? = null

        fun getRepository(context: Context): TaskRepository {
            return INSTANCE ?: synchronized(this) {
                val database = WorkspaceDatabase.getDatabase(context)
                val instance = TaskRepository(database.taskDao())
                INSTANCE = instance
                instance
            }
        }
    }
}