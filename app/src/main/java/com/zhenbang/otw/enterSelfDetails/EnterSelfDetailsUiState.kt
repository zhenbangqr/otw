package com.zhenbang.otw.enterSelfDetails

data class SelfDetailsUiState(
    val isLoading: Boolean = false,
    val isSaveSuccess: Boolean = false,
    val errorMessage: String? = null,
    // Input field states - consider making these StateFlows if needed elsewhere
    val displayName: String = "",
    val phoneNumber: String = "",
    // Add other fields as needed (e.g., dateOfBirth, bio)
)