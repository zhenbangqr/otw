package com.zhenbang.otw.newsApi

import com.google.gson.annotations.SerializedName

data class RequestNewsAPI(

    val apikey: String,

    @SerializedName("country")
    val country: String, //my

    @SerializedName("category")
    val category: String, //business,crime,education,food,health,politics,science,technology,world,lifestyle,tourism

    @SerializedName("language")
    val language: String,

    @SerializedName("domain")
    val domain: String,
)
