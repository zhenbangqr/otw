package com.zhenbang.otw.data.remote.api

import com.zhenbang.otw.data.model.ZpRequest
import com.zhenbang.otw.data.model.ZpResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ZpApi {
    @POST("v4/chat/completions") // <-- RELATIVE path
    suspend fun postApiData(
        @Body payload: ZpRequest // <-- request type
    ): ZpResponse // <-- response type
}