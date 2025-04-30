package com.zhenbang.otw.enterSelfDetails

data class SelfDetailsUiState(
    val isLoading: Boolean = false,
    val isSaveSuccess: Boolean = false,
    val errorMessage: String? = null,
    // Input field states
    val displayName: String = "", // Changed from firstName/lastName
    val phoneNumber: String = "",
    val birthdateMillis: Long? = null, // Store selected birthdate as Long (milliseconds)
    val bio: String = "", // Optional Bio field
    val showDatePicker: Boolean = false // State to control DatePickerDialog visibility
)