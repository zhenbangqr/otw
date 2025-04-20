package com.zhenbang.otw.departments

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.database.Department
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class DepartmentViewModel(private val repository: DepartmentRepository) : ViewModel() {

    val allDepartments: Flow<List<Department>> = repository.allDepartments

    private val defaultImageUrl = "https://dummyimage.com/400x400/D3D3D3/3F3F3F&text=No+Image"

    fun insertDepartment(departmentName: String, imageUrl: String?) = viewModelScope.launch {
        val finalImageUrl = if (imageUrl.isNullOrBlank()) {
            defaultImageUrl
        } else {
            imageUrl.trim()
        }
        repository.insertDepartment(departmentName, finalImageUrl)
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

