package com.example.again;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Background service that holds two Firestore snapshot listeners (buyer + seller chats)
 * and fires a local notification whenever a new message arrives for this user.
 *
 * Lifecycle:
 *  - Started (with "email" extra) when the user logs in or the app starts.
 *  - Stopped when the user logs out.
 */
public class MessageListenerService extends Service {

    /**
     * ChatFragment sets this to the currently open chat ID so we skip notifications
     * for messages the user is already reading.
     */
    public static volatile String currentOpenChatId = null;

    private String                           myEmail;
    private long                             serviceStartTs;
    private ChatPreferences                  chatPrefs;
    private final List<ListenerRegistration> listeners = new ArrayList<>();

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) myEmail = intent.getStringExtra("email");
        if (myEmail == null || myEmail.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Only re-initialise if this is a fresh start (not a delivery retry with null intent)
        serviceStartTs = System.currentTimeMillis();
        chatPrefs      = new ChatPreferences(this);

        attachListeners();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeListeners();
        super.onDestroy();
    }

    // ── Firestore listeners ───────────────────────────────────────────────────

    private void attachListeners() {
        removeListeners();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Chats where I'm the buyer
        listeners.add(
            db.collection("chats").whereEqualTo("buyerEmail", myEmail)
              .addSnapshotListener((qs, err) -> {
                  if (qs == null) return;
                  for (DocumentChange dc : qs.getDocumentChanges()) {
                      if (dc.getType() != DocumentChange.Type.REMOVED)
                          maybeNotify(dc.getDocument());
                  }
              }));

        // Chats where I'm the seller
        listeners.add(
            db.collection("chats").whereEqualTo("sellerEmail", myEmail)
              .addSnapshotListener((qs, err) -> {
                  if (qs == null) return;
                  for (DocumentChange dc : qs.getDocumentChanges()) {
                      if (dc.getType() != DocumentChange.Type.REMOVED)
                          maybeNotify(dc.getDocument());
                  }
              }));
    }

    private void removeListeners() {
        for (ListenerRegistration r : listeners) r.remove();
        listeners.clear();
    }

    // ── Notification logic ────────────────────────────────────────────────────

    private void maybeNotify(DocumentSnapshot doc) {
        String chatId = doc.getId();

        // Skip the chat the user is actively viewing
        if (chatId.equals(currentOpenChatId)) return;

        Long ts = doc.getLong("lastMessageTimestamp");
        if (ts == null || ts <= serviceStartTs) return;   // older than service start → skip

        // Don't notify for my own messages
        String senderEmail = doc.getString("lastSenderEmail");
        if (myEmail.equalsIgnoreCase(senderEmail)) return;

        // Already notified about this message
        long lastNotified = chatPrefs.getLastNotifiedTimestamp(chatId, myEmail);
        if (ts <= lastNotified) return;

        // Muted?
        if (chatPrefs.isMuted(chatId, myEmail)) return;

        // Build preview text
        String preview = doc.getString("lastMessageText");
        if (preview == null || preview.isEmpty()) preview = "New message";

        // Sender display name
        String buyerEmail = doc.getString("buyerEmail");
        String name = myEmail.equalsIgnoreCase(buyerEmail)
                ? doc.getString("sellerName")
                : doc.getString("buyerName");
        if (name == null || name.isEmpty()) name = "New message";

        chatPrefs.setLastNotifiedTimestamp(chatId, myEmail, ts);
        NotificationHelper.showMessageNotification(this, chatId, name, preview);
    }
}
