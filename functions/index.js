// Import necessary modules
const functions = require("firebase-functions"); // Keep for potential logger use or v1 compatibility if needed
const admin = require("firebase-admin");
// Import v2 trigger
const {onDocumentCreated} = require("firebase-functions/v2/firestore");

// Initialize Firebase Admin SDK
admin.initializeApp();

// --- MODIFIED: Use v2 syntax and include messageType ---
exports.sendChatNotification = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
    // Get the snapshot of the newly created document
    const snap = event.data; // Use event.data for v2 triggers
    if (!snap) {
        console.log("No data associated with the event.");
        return null; // Exit if no data
    }
    const messageData = snap.data();
    if (!messageData) {
        console.log("No message data found in snapshot.");
        return null; // Exit if no message data
    }

    // Get parameters from the context (event object in v2)
    const chatId = event.params.chatId;
    const messageId = event.params.messageId;
    console.log(`Processing message ${messageId} in chat ${chatId}`);

    // Extract necessary data from the message document
    const senderId = messageData.senderId;
    const receiverId = messageData.receiverId;
    const messageText = messageData.text;
    // --- NEW: Extract messageType ---
    // Ensure you store messageType as a string (e.g., "TEXT", "IMAGE") in Firestore
    const messageType = messageData.messageType || "TEXT"; // Default to TEXT if not present

    // Prevent self-notification or notifications for null receiver
    if (!receiverId || senderId === receiverId) {
         console.log(`Skipping notification: receiverId=${receiverId}, senderId=${senderId}`);
         return null;
    }

    console.log(`New ${messageType} message in chat ${chatId} from ${senderId} to ${receiverId}`);

    // Get sender's name
    let senderName = "Someone"; // Default sender name
    try {
        const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
        if (senderDoc.exists) {
             // Use 'name' field or fallback to 'username' or default
            senderName = senderDoc.data()?.name || senderDoc.data()?.displayName || "Someone";
        }
    } catch (error) {
        console.error(`Error fetching sender name for ID ${senderId}:`, error);
        // Continue with the default name if fetching fails
    }

    // Get recipient's FCM token and send notification
    try {
        const recipientDoc = await admin.firestore().collection("users").doc(receiverId).get();
        if (!recipientDoc.exists) {
            console.log(`Recipient document not found for ID ${receiverId}.`);
            return null;
        }

        const recipientData = recipientDoc.data();
        const fcmToken = recipientData?.fcmToken; // Use optional chaining
        if (!fcmToken) {
            console.log(`FCM token not found for recipient ID ${receiverId}.`);
            return null;
        }

        // --- MODIFIED: Construct payload with messageType ---
        // Determine notification body based on type
        let notificationBody = messageText || "Sent a message"; // Default body
        if (messageType === "IMAGE") {
            notificationBody = `${senderName} sent an image.`;
        } else if (messageType === "AUDIO") {
            notificationBody = `${senderName} sent an audio message.`;
        } else if (messageType === "LOCATION") {
            notificationBody = `${senderName} shared a location.`;
        } // Add more types as needed

        const payload = {
             notification: {
                 title: senderName, // Show sender's name as title
                 body: notificationBody, // Use the type-specific body
             },
             data: { // Custom data payload for your app to handle
                 type: "chat_message", // General type for routing in app
                 chatId: chatId,
                 senderId: senderId,
                 senderName: senderName,
                 // --- ADDED messageType to data payload ---
                 messageType: messageType, // e.g., "TEXT", "IMAGE", "AUDIO"
                 // Optionally include messageId if needed on the client immediately
                 messageId: messageId,
                 // You might want to include a snippet or URL depending on the type
                 // messageText: messageText, // Included in notification body, maybe not needed here?
                 // imageUrl: messageData.imageUrl || undefined, // Example for image type
             },
             token: fcmToken, // The recipient's device token
        };

        console.log("Sending FCM message payload:", JSON.stringify(payload)); // Log the full payload
        // Send the FCM message
        const response = await admin.messaging().send(payload);
        console.log("Successfully sent FCM message:", response);
        return response; // Return the result of sending

    } catch (error) {
        console.error(`Error fetching recipient token or sending message to ${receiverId}:`, error);
        return null; // Return null on error
    }
});

// Example helloWorld function (v2 syntax) - uncomment if needed
/*
const {onRequest} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");

exports.helloWorld = onRequest((request, response) => {
  logger.info("Hello logs!", {structuredData: true});
  response.send("Hello from Firebase v2!");
});
*/
