package com.zhenbang.otw

object Routes {
    const val LIVE_LOCATION = "live_location"
    // Route for messaging screen, takes userId as an argument
    const val MESSAGING = "messaging/{userId}"

    const val USER_LIST = "user_list_screen"

    const val CHAT_HISTORY = "chat_history_screen"

    // Helper function to create the route with the argument filled in
    fun messagingWithUser(userId: String) = "messaging/$userId"
}