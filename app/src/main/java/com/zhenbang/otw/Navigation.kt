package com.zhenbang.otw

object Routes {
    const val LIVE_LOCATION = "live_location"
    // Route for messaging screen, takes userId as an argument
    const val MESSAGING = "messaging/{userId}"

    // Helper function to create the route with the argument filled in
    fun messagingWithUser(userId: String) = "messaging/$userId"
}