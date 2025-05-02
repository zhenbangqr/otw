package com.zhenbang.otw.departments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.database.Department
import com.zhenbang.otw.database.DeptUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DepartmentViewModel(private val repository: DepartmentRepository) : ViewModel() {

    private val _selectedTabFlow = MutableStateFlow("Issues")
    val selectedTabFlow: StateFlow<String> = _selectedTabFlow.asStateFlow()

    private val defaultImageUrl = "https://dummyimage.com/400x400/D3D3D3/3F3F3F&text=No+Image"

    fun insertDepartment(departmentName: String, imageUrl: String?, creatorEmail: String) =
        viewModelScope.launch {
            val finalImageUrl = if (imageUrl.isNullOrBlank()) {
                defaultImageUrl
            } else {
                imageUrl.trim()
            }
            repository.insertDepartment(departmentName, finalImageUrl, creatorEmail)
        }

    fun updateDepartment(department: Department) = viewModelScope.launch {
        repository.updateDepartment(department)
    }

    fun deleteDepartment(department: Department) = viewModelScope.launch {
        repository.deleteDepartment(department)
    }

    fun getDepartmentById(id: Int): Flow<Department?> {
        return repository.getDepartmentById(id)
    }

    fun selectTab(tabName: String) {
        _selectedTabFlow.value = tabName
    }

    private val _userDepartments = MutableStateFlow<List<Department>>(emptyList())
    val userDepartments: StateFlow<List<Department>> = _userDepartments.asStateFlow()

    fun loadDepartmentsForCurrentUser(userEmail: String) {
        viewModelScope.launch {
            repository.getDepartmentsForUser(userEmail).collectLatest { departments ->
                _userDepartments.value = departments
            }
        }
    }

    fun getDeptUsersByDepartmentId(departmentId: Int): Flow<List<DeptUser>> {
        return repository.getDeptUsersByDepartmentId(departmentId)
    }

    fun insertDeptUser(departmentId: Int, userEmail: String) {
        viewModelScope.launch {
            repository.insertDeptUser(departmentId, userEmail)
        }
    }

    fun deleteDeptUser(departmentId: Int, userEmail: String) {
        viewModelScope.launch {
            repository.deleteDeptUser(departmentId, userEmail)
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DepartmentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DepartmentViewModel(DepartmentRepository.getRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

