package com.zhenbang.otw.zpApi

import retrofit2.http.Body
import retrofit2.http.POST

interface PostZPAPI {
    @POST("v4/chat/completions") // <-- RELATIVE path
    suspend fun postApiData(
        @Body payload: RequestZPAPI // <-- request type
    ): ResponseZPAPI // <-- response type
}