package com.example.again;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

public class NotificationHelper {

    public static final String CHANNEL_ID   = "again_messages";
    public static final String CHANNEL_NAME = "Messages";
    public static final String EXTRA_CHAT_ID = "chat_id";

    /** Create the notification channel (no-op on API < 26). Call once at app startup. */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("New message notifications from Again");
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    /** Fire a notification for a new message in the given chat. */
    public static void showMessageNotification(Context context,
                                               String chatId,
                                               String senderName,
                                               String messagePreview) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(EXTRA_CHAT_ID, chatId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context, chatId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_chat)
                .setContentTitle(senderName)
                .setContentText(messagePreview)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(chatId.hashCode(), builder.build());
    }

    /**
     * Check all chats the given user participates in for unread messages and
     * fire one notification per chat that has new messages since last notification.
     * This is called from BootReceiver and when the app starts.
     */
    public static void checkAndNotifyUnread(Context context, String myEmail) {
        if (myEmail == null || myEmail.isEmpty()) return;
        ChatPreferences chatPrefs = new ChatPreferences(context);
        List<Chat> chats = chatPrefs.getChatsForUser(myEmail);
        for (Chat chat : chats) {
            if (chatPrefs.isMuted(chat.getChatId(), myEmail)) continue;

            long lastNotified = chatPrefs.getLastNotifiedTimestamp(chat.getChatId(), myEmail);

            // Find messages from others, after lastNotified, not yet seen
            List<Message> messages = chatPrefs.getMessages(chat.getChatId());
            int newCount = 0;
            long latestTs = lastNotified;
            String latestPreview = "";
            for (Message m : messages) {
                if (!m.getSenderEmail().equalsIgnoreCase(myEmail)
                        && m.getTimestamp() > lastNotified
                        && !m.isDeleted()) {
                    newCount++;
                    latestTs = Math.max(latestTs, m.getTimestamp());
                    latestPreview = Message.TYPE_PRODUCT_CARD.equals(m.getType())
                            ? "📦 " + chat.getAdTitle() : m.getText();
                }
            }

            if (newCount > 0) {
                String preview = newCount == 1
                        ? latestPreview
                        : newCount + " new messages";
                showMessageNotification(context, chat.getChatId(),
                        chat.getOtherUserName(myEmail), preview);
                chatPrefs.setLastNotifiedTimestamp(chat.getChatId(), myEmail, latestTs);
            }
        }
    }
}
