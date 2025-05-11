package com.zhenbang.otw.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeptUserDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeptUser(deptUser: DeptUser)

    @Update
    suspend fun updateDeptUser(deptUser: DeptUser)

    @Delete
    suspend fun deleteDeptUser(deptUser: DeptUser)

    @Query("SELECT * FROM deptusers WHERE departmentId = :departmentId")
    fun getDeptUsersByDepartmentId(departmentId: Int): Flow<List<DeptUser>>

    @Query("SELECT * FROM deptusers WHERE userEmail = :userEmail")
    fun getDeptUsersByUserEmail(userEmail: String): Flow<List<DeptUser>>

    @Query("DELETE FROM deptusers WHERE departmentId = :departmentId AND userEmail = :userEmail")
    suspend fun deleteUserFromDepartment(departmentId: Int, userEmail: String)
}