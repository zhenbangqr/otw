package com.zhenbang.otw.util

/**
 * A generic sealed interface to represent UI states for data loading operations.
 *
 * @param T The type of data held in the Success state.
 */
sealed interface UiState<out T> {
    /** Represents an initial, idle state before any loading has started. */
    data object Idle : UiState<Nothing>

    /** Represents a state where data is currently being loaded. */
    data object Loading : UiState<Nothing>

    /**
     * Represents a state where data was successfully loaded.
     * @param data The loaded data of type T.
     */
    data class Success<T>(val data: T) : UiState<T>

    /**
     * Represents a state where an error occurred during data loading.
     * @param message A descriptive error message.
     */
    data class Error(val message: String) : UiState<Nothing>
}
