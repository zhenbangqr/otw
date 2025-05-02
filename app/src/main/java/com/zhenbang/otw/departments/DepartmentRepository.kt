package com.zhenbang.otw.departments

import android.content.Context
import android.util.Log
import com.zhenbang.otw.database.Department
import com.zhenbang.otw.database.DepartmentDao
import com.zhenbang.otw.database.DeptUser
import com.zhenbang.otw.database.DeptUserDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class DepartmentRepository(
    private val departmentDao: DepartmentDao,
    private val deptUserDao: DeptUserDao
) {

    val allDepartments: Flow<List<Department>> = departmentDao.getAllDepartments()

    suspend fun insertDepartment(departmentName: String, imageUrl: String?, creatorEmail: String) {
        if (creatorEmail.isBlank()) {
            Log.w(
                "DepartmentRepository",
                "Attempting to insert department with blank creatorEmail."
            )
        }
        withContext(Dispatchers.IO) {
            val department = Department(
                departmentName = departmentName,
                creatorEmail = creatorEmail,
                imageUrl = imageUrl
            )
            val departmentId =
                departmentDao.insertDepartment(department).toInt() // Get the generated ID
            deptUserDao.insertDeptUser(
                DeptUser(
                    departmentId = departmentId,
                    userEmail = creatorEmail
                )
            )
        }
    }

    suspend fun updateDepartment(department: Department) {
        withContext(Dispatchers.IO) {
            departmentDao.updateDepartment(department)
        }
    }

    suspend fun deleteDepartment(department: Department) {
        withContext(Dispatchers.IO) {
            departmentDao.deleteDepartment(department)
        }
    }

    fun getDepartmentById(departmentId: Int): Flow<Department?> {
        return departmentDao.getDepartmentById(departmentId)
    }

    fun getDepartmentsForUser(userEmail: String): Flow<List<Department>> {
        return departmentDao.getDepartmentsByUser(userEmail)
    }

    fun getDeptUsersByDepartmentId(departmentId: Int): Flow<List<DeptUser>> {
        return deptUserDao.getDeptUsersByDepartmentId(departmentId)
    }

    suspend fun insertDeptUser(departmentId: Int, userEmail: String) {
        withContext(Dispatchers.IO) {
            deptUserDao.insertDeptUser(
                DeptUser(
                    userEmail = userEmail,
                    departmentId = departmentId
                )
            )
        }
    }

    suspend fun deleteDeptUser(departmentId: Int, userEmail: String) {
        withContext(Dispatchers.IO) {
            deptUserDao.deleteUserFromDepartment(departmentId, userEmail)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DepartmentRepository? = null

        fun getRepository(context: Context): DepartmentRepository {
            return INSTANCE ?: synchronized(this) {
                val database = WorkspaceDatabase.getDatabase(context)
                val instance =
                    DepartmentRepository(database.departmentDao(), database.deptUserDao())
                INSTANCE = instance
                instance
            }
        }
    }
}