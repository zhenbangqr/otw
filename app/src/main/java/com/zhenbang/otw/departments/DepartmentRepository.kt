package com.zhenbang.otw.departments

import android.content.Context
import com.zhenbang.otw.database.Department
import com.zhenbang.otw.database.DepartmentDao
import com.zhenbang.otw.database.WorkspaceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DepartmentRepository(private val departmentDao: DepartmentDao) {

    val allDepartments: Flow<List<Department>> = departmentDao.getAllDepartments()

    suspend fun insertDepartment(departmentName: String, imageUrl: String?) {
        withContext(Dispatchers.IO) {
            departmentDao.insertDepartment(
                Department(
                    departmentName = departmentName,
                    imageUrl = imageUrl
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

    companion object {
        @Volatile
        private var INSTANCE: DepartmentRepository? = null

        fun getRepository(context: Context): DepartmentRepository {
            return INSTANCE ?: synchronized(this) {
                val database = WorkspaceDatabase.getDatabase(context)
                val instance = DepartmentRepository(database.departmentDao())
                INSTANCE = instance
                instance
            }
        }
    }
}