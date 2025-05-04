package com.zhenbang.otw.tasks

import android.content.Context
import com.zhenbang.otw.database.DeptUser
import com.zhenbang.otw.database.DeptUserDao
import com.zhenbang.otw.database.SubTask
import com.zhenbang.otw.database.SubTaskDao
import com.zhenbang.otw.database.Task
import com.zhenbang.otw.database.TaskAssignment
import com.zhenbang.otw.database.TaskAssignmentDao
import com.zhenbang.otw.database.TaskDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class TaskRepository(
    private val taskDao: TaskDao,
    private val deptUserDao: DeptUserDao,
    private val taskAssignmentDao: TaskAssignmentDao,
    private val subTaskDao: SubTaskDao
) {

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()

    suspend fun insertTask(task: Task, userEmails: List<String>, subTasks: List<SubTask> = emptyList()) {
        withContext(Dispatchers.IO) {
            val task = Task(
                taskTitle = task.taskTitle,
                taskDescription = task.taskDescription,
                departmentId = task.departmentId,
                creatorEmail = task.creatorEmail
            )
            val taskId = taskDao.insertTask(task).toInt()
            assignUsersToTask(taskId, userEmails)

            subTasks.forEach { subTask ->
                val subTaskToInsert = subTask.copy(taskId = taskId)
                subTaskDao.insertSubTask(subTaskToInsert)
            }
        }
    }

    suspend fun updateTask(task: Task, userEmails: List<String>, subTasks: List<SubTask> = emptyList()) {
        withContext(Dispatchers.IO) {
            taskDao.updateTask(task)
            assignUsersToTask(task.taskId, userEmails, clearExisting = true)

            // Update or insert subtasks.  This is more complex.
            val existingSubTasks = subTaskDao.getSubTasksByTaskId(task.taskId).firstOrNull() ?: emptyList()
            val existingSubTaskIds = existingSubTasks.map { it.subTaskId }.toSet()

            subTasks.forEach { subTask ->
                if (subTask.subTaskId == 0) { // New subtask
                    val subTaskToInsert = subTask.copy(taskId = task.taskId)
                    subTaskDao.insertSubTask(subTaskToInsert)
                } else if (existingSubTaskIds.contains(subTask.subTaskId)) { //update
                    subTaskDao.updateSubTask(subTask)
                }
                // Subtasks not in the incoming list are assumed to be deleted.  Handle this as needed.
            }
            //delete subtasks
            val subTasksToDelete = existingSubTasks.filter { existingSubTask ->
                subTasks.none { it.subTaskId == existingSubTask.subTaskId }
            }
            subTasksToDelete.forEach{
                subTaskDao.deleteSubTask(it)
            }
        }
    }

    suspend fun updateTaskCompletion(task: Task, isCompleted: Boolean) {
        withContext(Dispatchers.IO) {
            taskDao.updateTask(task.copy(isCompleted = isCompleted)) // Assuming your Task entity has an isCompleted field
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

    fun getDeptUsersByDepartmentId(departmentId: Int): Flow<List<DeptUser>> {
        return deptUserDao.getDeptUsersByDepartmentId(departmentId)
    }

    fun getTaskAssignmentsForUser(userEmail: String): Flow<List<TaskAssignment>> {
        return taskAssignmentDao.getTaskAssignmentsForUser(userEmail)
    }

    fun getAssignedUsersForTask(taskId: Int): Flow<List<TaskAssignment>> {
        return taskAssignmentDao.getTaskAssignmentsForTask(taskId)
    }

    private suspend fun assignUsersToTask(
        taskId: Int,
        userEmails: List<String>,
        clearExisting: Boolean = false
    ) {
        if (clearExisting) {
            taskAssignmentDao.deleteTaskAssignmentsByTaskId(taskId)
        }
        userEmails.forEach { userEmail ->
            taskAssignmentDao.insertTaskAssignment(
                TaskAssignment(
                    taskId = taskId,
                    userEmail = userEmail,
                )
            )
        }
    }

    suspend fun insertSubTask(subTask: SubTask) {
        withContext(Dispatchers.IO) {
            subTaskDao.insertSubTask(subTask)
        }
    }

    suspend fun updateSubTask(subTask: SubTask){
        withContext(Dispatchers.IO){
            subTaskDao.updateSubTask(subTask)
        }
    }

    suspend fun deleteSubTask(subTask: SubTask){
        withContext(Dispatchers.IO){
            subTaskDao.deleteSubTask(subTask)
        }
    }

    suspend fun updateSubTaskCompletion(subTask: SubTask, isCompleted: Boolean) {
        withContext(Dispatchers.IO) {
            subTaskDao.updateSubTask(subTask.copy(isCompleted = isCompleted))
        }
    }

    fun getSubTasksByTaskId(taskId: Int): Flow<List<SubTask>> {
        return subTaskDao.getSubTasksByTaskId(taskId)
    }

    fun getSubTaskById(subTaskId: Int): Flow<SubTask?> {
        return subTaskDao.getSubTaskById(subTaskId)
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskRepository? = null

        fun getRepository(context: Context): TaskRepository {
            return INSTANCE ?: synchronized(this) {
                val database = WorkspaceDatabase.getDatabase(context)
                val instance = TaskRepository(
                    database.taskDao(),
                    database.deptUserDao(),
                    database.taskAssignmentDao(),
                    database.subTaskDao()
                )
                INSTANCE = instance
                instance
            }
        }
    }
}