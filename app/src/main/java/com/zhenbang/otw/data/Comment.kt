package com.zhenbang.otw.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Comment(
    @DocumentId
    val id: String = "",

    val text: String = "",
    val authorUid: String = "",
    val authorEmail: String? = null,

    @ServerTimestamp
    val timestamp: Date? = null
) {
    constructor() : this("", "", "", null, null)
}