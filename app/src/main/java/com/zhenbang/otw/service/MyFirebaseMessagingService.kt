package com.zhenbang.otw.service // Or your preferred package

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zhenbang.otw.MainActivity // Your main activity
import com.zhenbang.otw.R // Your R file for resources like icons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM Service", "Refreshed token: $token")
        // Send token to your server/Firestore
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM Service", "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        remoteMessage.data.isNotEmpty().let {
            Log.d("FCM Service", "Message data payload: " + remoteMessage.data)
            // TODO: Handle data payload - parse senderId, message text, chatId etc.
            // Example: val senderName = remoteMessage.data["senderName"] ?: "New Message"
            // Example: val messageBody = remoteMessage.data["messageBody"] ?: ""
            // Example: val chatId = remoteMessage.data["chatId"]
        }

        // Check if message contains a notification payload. (Often handled by system when app is backgrounded)
        remoteMessage.notification?.let {
            Log.d("FCM Service", "Message Notification Body: ${it.body}")
            val title = it.title ?: "New Message"
            val body = it.body ?: ""
            // Show the notification IF the app is in foreground, or based on your logic
            // If app is in background, system usually shows the notification payload automatically
            // You might want to only use data payloads for full control.
            showNotification(title, body)
        }
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "new_message_channel" // Choose a channel ID
        val channelName = "New Messages" // User visible channel name

        // Create Notification Channel (Required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for new chat messages"
                // Configure channel options (sound, vibration, etc.) if needed
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Intent to open when notification is tapped (e.g., open MainActivity or specific chat)
        // TODO: Make this intent smarter - navigate directly to the chat using chatId from data payload
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // putExtra("chatId", chatId) // Add chat ID if you have it
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_message) // !! IMPORTANT: Create this small icon (white/transparent)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Dismiss notification when tapped
            .setContentIntent(pendingIntent) // Set the intent

        // Check for notification permission (Android 13+)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w("FCM Service", "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
            // Consider requesting permission from the user elsewhere in your app.
            return
        }

        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            val notificationId = System.currentTimeMillis().toInt() // Simple unique ID
            notify(notificationId, builder.build())
            Log.d("FCM Service", "Notification shown with ID: $notificationId")
        }
    }


    private fun sendRegistrationToServer(token: String?) {
        // Re-use the saveTokenToFirestore logic (make it accessible or duplicate)
        scope.launch {
            saveTokenToFirestore(token)
        }
    }

    // Helper function (duplicate or move to shared location)
    suspend fun saveTokenToFirestore(token: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && token != null) {
            try {
                val userDocRef = Firebase.firestore.collection("users").document(userId)
                userDocRef.set(mapOf("fcmToken" to token), SetOptions.merge()).await()
                Log.d("FCM Token Update", "Token refreshed and saved to Firestore for user $userId")
            } catch (e: Exception) {
                Log.e("FCM Token Update", "Error saving refreshed token to Firestore", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // Cancel coroutines when service is destroyed
    }
}