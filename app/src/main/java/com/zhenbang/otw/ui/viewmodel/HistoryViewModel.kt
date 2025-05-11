package com.zhenbang.otw.ui.viewmodel // Or your ViewModel package

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhenbang.otw.data.repository.HistoryRepository
import com.zhenbang.otw.data.local.SummaryHistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


class HistoryViewModel(
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val summaryHistory: StateFlow<List<SummaryHistoryEntity>> =
        historyRepository.allSummaries.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )

    // --- NEW: Function to handle deletion ---
    fun deleteHistoryItem(item: SummaryHistoryEntity) {
        viewModelScope.launch {
            try {
                historyRepository.deleteSummary(item)
                Log.i("HistoryViewModel", "Deleted item with id: ${item.id}")
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error deleting item id: ${item.id}", e)
                // Optionally expose an error state to the UI
            }
        }
    }

    // --- NEW: Function to handle update ---
    // Takes the original item and the new text
    fun updateHistoryItemSummary(item: SummaryHistoryEntity, newSummary: String) {
        // Create an updated entity object
        val updatedItem = item.copy(aiSummaryResponse = newSummary)
        viewModelScope.launch {
            try {
                historyRepository.updateSummary(updatedItem)
                Log.i("HistoryViewModel", "Updated item with id: ${item.id}")
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error updating item id: ${item.id}", e)
                // Optionally expose an error state to the UI
            }
        }
    }
}