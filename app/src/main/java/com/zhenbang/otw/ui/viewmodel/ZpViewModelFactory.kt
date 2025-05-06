package com.zhenbang.otw.ui.viewmodel // Or your ViewModel package

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhenbang.otw.data.local.AppDatabase
import com.zhenbang.otw.repository.HistoryRepository
// Import any other dependencies ZpViewModel needs (e.g., API repository)

class ZpViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ZpViewModel::class.java)) {
            // Manually create dependencies
            val historyDao = AppDatabase.getDatabase(application).summaryHistoryDao()
            val historyRepository = HistoryRepository(historyDao)
            // Create other dependencies ZpViewModel might need (e.g., apiRepository)
            // val apiRepository = YourApiRepository(...)
            @Suppress("UNCHECKED_CAST")
            // Pass all required dependencies to the ZpViewModel constructor
            return ZpViewModel(historyRepository /*, apiRepository */) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}