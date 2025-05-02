const functions = require("firebase-functions"); // Keep for logger potentially? Or remove if only using v2. Let's keep it for now.
const admin = require("firebase-admin");
// --- NEW: Import v2 trigger ---
const {onDocumentCreated} = require("firebase-functions/v2/firestore");

admin.initializeApp();

// --- MODIFIED: Use v2 syntax ---
exports.sendChatNotification = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
    // Get the snapshot of the newly created document
    const snap = event.data; // <-- Change: Use event.data
    if (!snap) {
        console.log("No data associated with the event.");
        return null;
    }
    const messageData = snap.data();
    if (!messageData) {
        console.log("No message data found in snapshot.");
        return null;
    }

    // Get parameters from the context (event object in v2)
    const chatId = event.params.chatId; // <-- Change: Use event.params
    const messageId = event.params.messageId; // Can get messageId too if needed
    console.log(`Processing message ${messageId} in chat ${chatId}`);


    const senderId = messageData.senderId;
    const receiverId = messageData.receiverId;
    const messageText = messageData.text;

    // Prevent self-notification or notifications for null receiver
    if (!receiverId || senderId === receiverId) {
         console.log("Skipping notification for self or null receiver.");
         return null;
    }

    console.log(`New message in chat ${chatId} from ${senderId} to ${receiverId}`);

    // Get sender's name (remains the same, uses admin SDK)
    let senderName = "Someone";
    try {
        const senderDoc = await admin.firestore().collection("users").doc(senderId).get();
        if (senderDoc.exists && senderDoc.data().name) {
            senderName = senderDoc.data().name;
        }
    } catch (error) {
        console.error("Error fetching sender name:", error);
    }

    // Get recipient's FCM token (remains the same, uses admin SDK)
    try {
        const recipientDoc = await admin.firestore().collection("users").doc(receiverId).get();
        // ... (rest of the token fetching and sending logic remains the same) ...
        if (!recipientDoc.exists) { /* ... */ return null; }
        const recipientData = recipientDoc.data();
        const fcmToken = recipientData.fcmToken;
        if (!fcmToken) { /* ... */ return null; }

        const payload = { /* ... construct payload ... */
             notification: { title: `${senderName}`, body: messageText },
             data: { type: "chat_message", chatId: chatId, senderId: senderId, senderName: senderName, messageText: messageText },
             token: fcmToken,
        };

        console.log("Sending FCM message to token:", fcmToken);
        return admin.messaging().send(payload);

    } catch (error) {
        console.error("Error fetching recipient token or sending message:", error);
        return null;
    }
});

// ... (helloWorld function likely still uses v1 'onRequest' if uncommented,
// or would need conversion to v2 'https.onRequest' if using v2 consistently) ...
// const {onRequest} = require("firebase-functions/v2/https");
// const logger = require("firebase-functions/logger");
// exports.helloWorld = onRequest((request, response) => {
//   logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });