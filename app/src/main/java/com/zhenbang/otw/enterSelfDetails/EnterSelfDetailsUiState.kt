package com.zhenbang.otw.enterSelfDetails

data class SelfDetailsUiState(
    val isLoading: Boolean = false,
    val isSaveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val birthdateMillis: Long? = null,
    val bio: String = "",
    val showDatePicker: Boolean = false
)