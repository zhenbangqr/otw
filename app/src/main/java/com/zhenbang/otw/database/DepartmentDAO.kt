package com.zhenbang.otw.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DepartmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(department: Department): Long

    @Update
    suspend fun updateDepartment(department: Department)

    @Delete
    suspend fun deleteDepartment(department: Department)

    @Query("SELECT * FROM departments")
    fun getAllDepartments(): Flow<List<Department>>

    @Query("SELECT * FROM departments WHERE departmentId = :departmentId")
    fun getDepartmentById(departmentId: Int): Flow<Department?>

    @Query("UPDATE departments SET departmentName = :newName WHERE departmentId = :departmentId")
    suspend fun updateDepartmentNameById(departmentId: Int, newName: String)

    @Query(
        """
        SELECT d.* FROM departments d
        INNER JOIN deptusers du ON d.departmentId = du.departmentId
        WHERE du.userEmail = :email
    """
    )
    fun getDepartmentsByUser(email: String): Flow<List<Department>>
}