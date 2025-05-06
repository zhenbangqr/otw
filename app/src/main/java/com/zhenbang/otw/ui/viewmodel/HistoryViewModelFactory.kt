package com.zhenbang.otw.ui.viewmodel // Or your ViewModel package

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhenbang.otw.data.local.AppDatabase
import com.zhenbang.otw.data.repository.HistoryRepository

class HistoryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            // Manually create dependencies: Database -> DAO -> Repository
            val dao = AppDatabase.getDatabase(application).summaryHistoryDao()
            val repository = HistoryRepository(dao)
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}