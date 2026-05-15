package com.example.again;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All chat and message data lives in Firestore.
 *
 * Firestore structure:
 *   chats/{chatId}                       — chat metadata
 *   chats/{chatId}/messages/{msgId}      — individual messages
 *
 * Per-user preferences (mute / archive / delete / lastSeen / notification tracking)
 * stay in SharedPreferences — they are device-local settings, not shared data.
 */
public class ChatPreferences {

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface ChatsCallback    { void onResult(List<Chat> chats); }
    public interface ChatCallback     { void onResult(Chat chat); }
    public interface MessagesCallback { void onResult(List<Message> messages); }
    public interface StringCallback   { void onResult(String value); }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String COL_CHATS    = "chats";
    private static final String COL_MESSAGES = "messages";
    private static final String PREFS_NAME   = "again_chats";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context           context;
    private final FirebaseFirestore db;
    private final SharedPreferences prefs;

    public ChatPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.db      = FirebaseFirestore.getInstance();
        this.prefs   = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Find / create chats ───────────────────────────────────────────────────

    /**
     * Find an existing chat between buyer and seller about this ad.
     * Result is null if no chat exists.
     */
    public void findChat(String buyerEmail, String sellerEmail, String adId, ChatCallback cb) {
        // Single-field query (auto-indexed) — filter the rest client-side
        db.collection(COL_CHATS)
            .whereEqualTo("buyerEmail", buyerEmail)
            .get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Chat c = docToChat(doc);
                    if (c != null
                            && c.getSellerEmail().equalsIgnoreCase(sellerEmail)
                            && c.getAdId().equals(adId)) {
                        cb.onResult(c);
                        return;
                    }
                }
                cb.onResult(null);
            })
            .addOnFailureListener(e -> cb.onResult(null));
    }

    /**
     * Create a new chat and send the opening product-card + "I'm interested" messages.
     * Calls back with the chatId, or null on failure.
     */
    public void startChat(String buyerEmail, String buyerName,
                          String sellerEmail, String sellerName,
                          String adId, String adTitle, double adPrice,
                          StringCallback cb) {
        String chatId = UUID.randomUUID().toString();
        Chat chat = new Chat(chatId, buyerEmail, buyerName,
                sellerEmail, sellerName, adId, adTitle, adPrice);

        db.collection(COL_CHATS).document(chatId)
            .set(chatToMap(chat))
            .addOnSuccessListener(v -> {
                // Product-card message
                String cardText = Message.makeProductCardText(
                        adTitle, String.format(Locale.US, "₪%,.2f", adPrice));
                Message cardMsg = new Message(UUID.randomUUID().toString(), chatId, buyerEmail,
                        cardText, System.currentTimeMillis(), Message.TYPE_PRODUCT_CARD);

                // "I'm interested" text
                Message textMsg = new Message(UUID.randomUUID().toString(), chatId, buyerEmail,
                        "I'm interested", System.currentTimeMillis() + 1, Message.TYPE_TEXT);

                sendMessageFirestore(chat, cardMsg, () ->
                        sendMessageFirestore(chat, textMsg, () -> cb.onResult(chatId)));
            })
            .addOnFailureListener(e -> cb.onResult(null));
    }

    // ── Fetch chats ───────────────────────────────────────────────────────────

    public void getChatById(String chatId, ChatCallback cb) {
        db.collection(COL_CHATS).document(chatId).get()
            .addOnSuccessListener(doc -> cb.onResult(docToChat(doc)))
            .addOnFailureListener(e -> cb.onResult(null));
    }

    /** All chats the user participates in (not deleted by them), newest-first. */
    public void getChatsForUser(String email, ChatsCallback cb) {
        final List<Chat> merged  = new ArrayList<>();
        final int[]      pending = {2};

        // Query 1: I'm the buyer
        db.collection(COL_CHATS).whereEqualTo("buyerEmail", email).get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    if (inDeletedBy(doc, email)) continue;  // hidden for this user
                    Chat c = docToChat(doc);
                    if (c != null) merged.add(c);
                }
                pending[0]--;
                if (pending[0] == 0) sortAndReturn(merged, cb);
            })
            .addOnFailureListener(e -> {
                pending[0]--;
                if (pending[0] == 0) sortAndReturn(merged, cb);
            });

        // Query 2: I'm the seller
        db.collection(COL_CHATS).whereEqualTo("sellerEmail", email).get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    if (inDeletedBy(doc, email)) continue;  // hidden for this user
                    Chat c = docToChat(doc);
                    if (c == null) continue;
                    boolean dup = false;
                    for (Chat ex : merged) {
                        if (ex.getChatId().equals(c.getChatId())) { dup = true; break; }
                    }
                    if (!dup) merged.add(c);
                }
                pending[0]--;
                if (pending[0] == 0) sortAndReturn(merged, cb);
            })
            .addOnFailureListener(e -> {
                pending[0]--;
                if (pending[0] == 0) sortAndReturn(merged, cb);
            });
    }

    private void sortAndReturn(List<Chat> list, ChatsCallback cb) {
        list.sort((a, b) -> Long.compare(b.getLastMessageTimestamp(), a.getLastMessageTimestamp()));
        cb.onResult(list);
    }

    /** Active (non-archived) chats. */
    public void getActiveChatsForUser(String email, ChatsCallback cb) {
        getChatsForUser(email, chats -> {
            List<Chat> result = new ArrayList<>();
            for (Chat c : chats) {
                if (!isArchived(c.getChatId(), email)) result.add(c);
            }
            cb.onResult(result);
        });
    }

    /** Archived chats. */
    public void getArchivedChatsForUser(String email, ChatsCallback cb) {
        getChatsForUser(email, chats -> {
            List<Chat> result = new ArrayList<>();
            for (Chat c : chats) {
                if (isArchived(c.getChatId(), email)) result.add(c);
            }
            cb.onResult(result);
        });
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    /** One-time fetch. Useful for notifications. */
    public void getMessages(String chatId, MessagesCallback cb) {
        db.collection(COL_CHATS).document(chatId)
            .collection(COL_MESSAGES)
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener(qs -> {
                List<Message> list = new ArrayList<>();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Message m = docToMessage(doc);
                    if (m != null) list.add(m);
                }
                cb.onResult(list);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    /**
     * Real-time snapshot listener — fires immediately with current data, then on
     * every change. Store the returned registration and call remove() on it when
     * the fragment is destroyed.
     */
    /** Real-time listener on the chat document itself (e.g. to watch for disabled state). */
    public ListenerRegistration listenToChat(String chatId, ChatCallback cb) {
        return db.collection(COL_CHATS).document(chatId)
            .addSnapshotListener((doc, err) -> cb.onResult(docToChat(doc)));
    }

    public ListenerRegistration listenForMessages(String chatId, MessagesCallback cb) {
        return db.collection(COL_CHATS).document(chatId)
            .collection(COL_MESSAGES)
            .orderBy("timestamp")
            .addSnapshotListener((qs, err) -> {
                if (qs == null) { cb.onResult(new ArrayList<>()); return; }
                List<Message> list = new ArrayList<>();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Message m = docToMessage(doc);
                    if (m != null) list.add(m);
                }
                cb.onResult(list);
            });
    }

    // ── Send / edit / delete messages ─────────────────────────────────────────

    /** Fire-and-forget send. The real-time listener in ChatFragment will update the UI. */
    public void sendMessage(String chatId, String senderEmail, String text) {
        getChatById(chatId, chat -> {
            if (chat == null) return;
            Message msg = new Message(UUID.randomUUID().toString(), chatId,
                    senderEmail, text, System.currentTimeMillis(), Message.TYPE_TEXT);
            sendMessageFirestore(chat, msg, null);

            // If the recipient had soft-deleted the chat, restore it
            String other = chat.getOtherUserEmail(senderEmail);
            if (other != null && isDeleted(chatId, other)) unDeleteChat(chatId, other);
        });
    }

    /** Mark a message as deleted (sender only). */
    public void deleteMessage(String chatId, String msgId, String senderEmail) {
        db.collection(COL_CHATS).document(chatId)
            .collection(COL_MESSAGES).document(msgId)
            .get()
            .addOnSuccessListener(doc -> {
                Message m = docToMessage(doc);
                if (m == null || !m.getSenderEmail().equalsIgnoreCase(senderEmail)) return;
                db.collection(COL_CHATS).document(chatId)
                    .collection(COL_MESSAGES).document(msgId)
                    .update("deleted", true);
            });
    }

    /** Edit a message (sender only, within the edit window). Calls onFail if not possible. */
    public void editMessage(String chatId, String msgId, String senderEmail,
                            String newText, Runnable onFail) {
        db.collection(COL_CHATS).document(chatId)
            .collection(COL_MESSAGES).document(msgId)
            .get()
            .addOnSuccessListener(doc -> {
                Message m = docToMessage(doc);
                if (m == null
                        || !m.getSenderEmail().equalsIgnoreCase(senderEmail)
                        || !m.canEdit()) {
                    if (onFail != null) onFail.run();
                    return;
                }
                Map<String, Object> upd = new HashMap<>();
                upd.put("text", newText);
                upd.put("editedTimestamp", System.currentTimeMillis());
                db.collection(COL_CHATS).document(chatId)
                    .collection(COL_MESSAGES).document(msgId)
                    .update(upd);
            })
            .addOnFailureListener(e -> { if (onFail != null) onFail.run(); });
    }

    // ── Chat-level operations ─────────────────────────────────────────────────

    /**
     * Soft-delete: hides the chat from this user's view.
     * Sends a system notice to the other participant, then adds the user's email
     * to the Firestore `deletedBy` array. If both participants have now deleted,
     * the entire chat and its messages are permanently purged.
     */
    public void deleteChat(String chatId, String email, String displayName) {
        // 1. Local prefs → immediate UI update on this device
        UserChatSetting s = getSetting(chatId, email);
        s.deleted = true;
        saveSetting(chatId, email, s);

        // 2. Send system notice, then update Firestore
        getChatById(chatId, chat -> {
            if (chat != null) {
                String notice = displayName + " deleted this conversation";
                Message sys = new Message(UUID.randomUUID().toString(), chatId, email,
                        notice, System.currentTimeMillis(), Message.TYPE_SYSTEM);
                sendMessageFirestore(chat, sys, null);
            }

            // 3. Add email to deletedBy
            db.collection(COL_CHATS).document(chatId)
                .update("deletedBy", FieldValue.arrayUnion(email))
                .addOnSuccessListener(v -> {
                    // 4. Check if both participants have now deleted → purge
                    db.collection(COL_CHATS).document(chatId).get()
                        .addOnSuccessListener(doc -> {
                            if (chat == null) return;
                            if (inDeletedBy(doc, chat.getBuyerEmail())
                                    && inDeletedBy(doc, chat.getSellerEmail())) {
                                purgeChat(chatId);
                            }
                        });
                });
        });
    }

    /** Permanently delete a chat document and all its messages. */
    private void purgeChat(String chatId) {
        db.collection(COL_CHATS).document(chatId)
            .collection(COL_MESSAGES).get()
            .addOnSuccessListener(qs -> {
                WriteBatch batch = db.batch();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    batch.delete(doc.getReference());
                }
                // Delete the chat document itself in the same batch
                batch.delete(db.collection(COL_CHATS).document(chatId));
                batch.commit();
            });
    }

    /** Update the stored ad title in every chat that references this ad. */
    public void updateAdTitleInChats(String adId, String newTitle) {
        db.collection(COL_CHATS).whereEqualTo("adId", adId).get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    db.collection(COL_CHATS).document(doc.getId())
                        .update("adTitle", newTitle != null ? newTitle : "");
                }
            });
    }

    /** Send a "marked as sold" system message to all chats for this ad. */
    public void sendSoldNotification(String adId, String sellerEmail, String productName) {
        sendSystemToAdChats(adId, sellerEmail,
                "🏷️ \"" + productName + "\" has been marked as sold.");
    }

    /** Send an "available again" system message to all chats for this ad. */
    public void sendUnsoldNotification(String adId, String sellerEmail, String productName) {
        sendSystemToAdChats(adId, sellerEmail,
                "✅ \"" + productName + "\" is available again.");
    }

    /** Send an "ad was edited" system message to all chats for this ad. */
    public void sendEditedNotification(String adId, String sellerEmail, String productName) {
        sendSystemToAdChats(adId, sellerEmail,
                "✏️ \"" + productName + "\" has been updated by the seller.");
    }

    /** Send an "ad was deleted" system message to all chats for this ad. */
    public void sendAdDeletedNotification(String adId, String sellerEmail, String productName) {
        sendSystemToAdChats(adId, sellerEmail,
                "🗑️ \"" + productName + "\" has been deleted by the seller.");
    }

    /**
     * Called when a user deletes their account. For every chat they were part of:
     *  - Checks whether the OTHER participant's account still exists in Firestore.
     *  - If the other user is gone too → purge the chat completely (no one left).
     *  - If the other user still exists → mark the chat as disabled and send a
     *    system message so they can see what happened but can't reply.
     */
    public void handleUserAccountDeletion(String userEmail, String displayName, Runnable onDone) {
        final List<DocumentSnapshot> allDocs = new ArrayList<>();
        final int[]                  pending = {2};

        Runnable afterBothQueries = () -> {
            if (allDocs.isEmpty()) {
                if (onDone != null) onDone.run();
                return;
            }
            final int[] remaining = {allDocs.size()};
            Runnable dec = () -> {
                remaining[0]--;
                if (remaining[0] == 0 && onDone != null) onDone.run();
            };

            String notice = "👤 " + displayName + " has deleted their account.";

            for (DocumentSnapshot doc : allDocs) {
                Chat chat = docToChat(doc);
                if (chat == null) { dec.run(); continue; }

                String otherEmail = chat.getOtherUserEmail(userEmail);

                // Check if the other user's account actually still exists in Firestore
                db.collection("users")
                    .whereEqualTo("email", otherEmail)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(qs -> {
                        if (qs.isEmpty()) {
                            // Other user's account is also gone → purge the chat entirely
                            purgeChat(chat.getChatId());
                            dec.run();
                        } else {
                            // Other user still exists → disable + notify them
                            db.collection(COL_CHATS).document(chat.getChatId())
                                .update("disabled", true)
                                .addOnCompleteListener(t -> {
                                    Message sys = new Message(UUID.randomUUID().toString(),
                                            chat.getChatId(), userEmail,
                                            notice, System.currentTimeMillis(), Message.TYPE_SYSTEM);
                                    sendMessageFirestore(chat, sys, dec);
                                });
                        }
                    })
                    .addOnFailureListener(e -> {
                        // On failure, play it safe: disable instead of purging
                        db.collection(COL_CHATS).document(chat.getChatId())
                            .update("disabled", true)
                            .addOnCompleteListener(t -> dec.run());
                    });
            }
        };

        db.collection(COL_CHATS).whereEqualTo("buyerEmail", userEmail).get()
            .addOnSuccessListener(qs -> {
                allDocs.addAll(qs.getDocuments());
                pending[0]--;
                if (pending[0] == 0) afterBothQueries.run();
            })
            .addOnFailureListener(e -> {
                pending[0]--;
                if (pending[0] == 0) afterBothQueries.run();
            });

        db.collection(COL_CHATS).whereEqualTo("sellerEmail", userEmail).get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    boolean dup = false;
                    for (DocumentSnapshot ex : allDocs) {
                        if (ex.getId().equals(doc.getId())) { dup = true; break; }
                    }
                    if (!dup) allDocs.add(doc);
                }
                pending[0]--;
                if (pending[0] == 0) afterBothQueries.run();
            })
            .addOnFailureListener(e -> {
                pending[0]--;
                if (pending[0] == 0) afterBothQueries.run();
            });
    }

    private void sendSystemToAdChats(String adId, String sellerEmail, String text) {
        db.collection(COL_CHATS).whereEqualTo("adId", adId).get()
            .addOnSuccessListener(qs -> {
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Chat chat = docToChat(doc);
                    if (chat == null
                            || !chat.getSellerEmail().equalsIgnoreCase(sellerEmail)
                            || isDeleted(chat.getChatId(), sellerEmail)) continue;

                    Message sys = new Message(UUID.randomUUID().toString(),
                            chat.getChatId(), sellerEmail,
                            text, System.currentTimeMillis(), Message.TYPE_SYSTEM);
                    sendMessageFirestore(chat, sys, null);

                    // Restore for the buyer if they had deleted the chat
                    String buyer = chat.getBuyerEmail();
                    if (isDeleted(chat.getChatId(), buyer)) unDeleteChat(chat.getChatId(), buyer);
                }
            });
    }

    // ── Per-user local settings (SharedPreferences) ───────────────────────────

    public void muteChat(String chatId, String email, boolean muted) {
        UserChatSetting s = getSetting(chatId, email);
        s.muted = muted;
        saveSetting(chatId, email, s);
    }

    public void archiveChat(String chatId, String email, boolean archived) {
        UserChatSetting s = getSetting(chatId, email);
        s.archived = archived;
        saveSetting(chatId, email, s);
    }

    public void unDeleteChat(String chatId, String email) {
        UserChatSetting s = getSetting(chatId, email);
        s.deleted  = false;
        s.archived = false;
        saveSetting(chatId, email, s);
    }

    public boolean isMuted(String chatId, String email)    { return getSetting(chatId, email).muted; }
    public boolean isArchived(String chatId, String email) { return getSetting(chatId, email).archived; }
    public boolean isDeleted(String chatId, String email)  { return getSetting(chatId, email).deleted; }

    // ── Unread count ──────────────────────────────────────────────────────────

    /**
     * Lightweight unread indicator using only local lastSeen timestamp and the
     * chat's lastMessageTimestamp (already loaded in the list).
     * Returns 1 if there's likely an unread message, 0 otherwise.
     */
    public int getUnreadCount(Chat chat, String email) {
        if (chat == null) return 0;
        long lastSeen = getSetting(chat.getChatId(), email).lastSeen;
        return chat.getLastMessageTimestamp() > lastSeen ? 1 : 0;
    }

    public void markChatRead(String chatId, String email) {
        UserChatSetting s = getSetting(chatId, email);
        s.lastSeen = System.currentTimeMillis();
        saveSetting(chatId, email, s);
    }

    // ── Notification tracking ─────────────────────────────────────────────────

    public long getLastNotifiedTimestamp(String chatId, String email) {
        String key = "notif_" + email;
        for (String s : prefs.getStringSet(key, new LinkedHashSet<>())) {
            String[] p = s.split("\\|", 2);
            if (p.length == 2 && p[0].equals(chatId)) {
                try { return Long.parseLong(p[1]); } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    public void setLastNotifiedTimestamp(String chatId, String email, long ts) {
        String key = "notif_" + email;
        Set<String> set = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        set.removeIf(s -> s.startsWith(chatId + "|"));
        set.add(chatId + "|" + ts);
        prefs.edit().putStringSet(key, set).apply();
    }

    // ── Internal: write a message to Firestore ────────────────────────────────

    private void sendMessageFirestore(Chat chat, Message msg, Runnable onDone) {
        db.collection(COL_CHATS).document(chat.getChatId())
            .collection(COL_MESSAGES).document(msg.getMsgId())
            .set(messageToMap(msg))
            .addOnSuccessListener(v -> {
                // Update the chat's last-message preview
                String preview;
                if (msg.isDeleted()) {
                    preview = "Message deleted";
                } else if (Message.TYPE_PRODUCT_CARD.equals(msg.getType())) {
                    preview = "📦 " + chat.getAdTitle();
                } else if (Message.TYPE_SYSTEM.equals(msg.getType())) {
                    preview = msg.getText();
                } else {
                    preview = msg.getText();
                }
                Map<String, Object> upd = new HashMap<>();
                upd.put("lastMessageText", preview);
                upd.put("lastMessageTimestamp", msg.getTimestamp());
                upd.put("lastSenderEmail", msg.getSenderEmail());
                db.collection(COL_CHATS).document(chat.getChatId()).update(upd);
                if (onDone != null) onDone.run();
            });
    }

    // ── Firestore serialization ───────────────────────────────────────────────

    private Map<String, Object> chatToMap(Chat chat) {
        Map<String, Object> m = new HashMap<>();
        m.put("chatId",               chat.getChatId());
        m.put("buyerEmail",           chat.getBuyerEmail());
        m.put("buyerName",            chat.getBuyerName());
        m.put("sellerEmail",          chat.getSellerEmail());
        m.put("sellerName",           chat.getSellerName());
        m.put("adId",                 chat.getAdId());
        m.put("adTitle",              chat.getAdTitle());
        m.put("adPrice",              chat.getAdPrice());
        m.put("lastMessageText",      chat.getLastMessageText());
        m.put("lastMessageTimestamp", chat.getLastMessageTimestamp());
        m.put("disabled",             chat.isDisabled());
        return m;
    }

    private Chat docToChat(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        try {
            String chatId    = s(doc, "chatId");
            if (chatId.isEmpty()) chatId = doc.getId();
            Chat c = new Chat(chatId,
                    s(doc, "buyerEmail"),  s(doc, "buyerName"),
                    s(doc, "sellerEmail"), s(doc, "sellerName"),
                    s(doc, "adId"),        s(doc, "adTitle"),
                    doc.getDouble("adPrice") != null ? doc.getDouble("adPrice") : 0);
            long ts = doc.getLong("lastMessageTimestamp") != null
                    ? doc.getLong("lastMessageTimestamp") : 0;
            c.setLastMessageTimestamp(ts);
            c.setLastMessageText(s(doc, "lastMessageText"));
            c.setDisabled(Boolean.TRUE.equals(doc.getBoolean("disabled")));
            return c;
        } catch (Exception e) { return null; }
    }

    private Map<String, Object> messageToMap(Message msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("msgId",           msg.getMsgId());
        m.put("chatId",          msg.getChatId());
        m.put("senderEmail",     msg.getSenderEmail());
        m.put("text",            msg.getText());
        m.put("timestamp",       msg.getTimestamp());
        m.put("type",            msg.getType());
        m.put("deleted",         msg.isDeleted());
        m.put("editedTimestamp", msg.getEditedTimestamp());
        return m;
    }

    private Message docToMessage(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        try {
            String msgId  = s(doc, "msgId");
            if (msgId.isEmpty()) msgId = doc.getId();
            String type = s(doc, "type");
            if (type.isEmpty()) type = Message.TYPE_TEXT;
            long ts = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0;
            long editedTs = doc.getLong("editedTimestamp") != null
                    ? doc.getLong("editedTimestamp") : 0;
            boolean deleted = Boolean.TRUE.equals(doc.getBoolean("deleted"));
            Message m = new Message(msgId, s(doc, "chatId"),
                    s(doc, "senderEmail"), s(doc, "text"), ts, type);
            m.setDeleted(deleted);
            m.setEditedTimestamp(editedTs);
            return m;
        } catch (Exception e) { return null; }
    }

    private String s(DocumentSnapshot doc, String key) {
        String v = doc.getString(key);
        return v != null ? v : "";
    }

    /** True if the given email appears in the document's `deletedBy` array. */
    @SuppressWarnings("unchecked")
    private boolean inDeletedBy(DocumentSnapshot doc, String email) {
        if (doc == null || email == null) return false;
        List<String> deletedBy = (List<String>) doc.get("deletedBy");
        if (deletedBy == null) return false;
        for (String e : deletedBy) {
            if (email.equalsIgnoreCase(e)) return true;
        }
        return false;
    }

    // ── Local settings helpers ────────────────────────────────────────────────

    private static class UserChatSetting {
        boolean muted    = false;
        boolean archived = false;
        boolean deleted  = false;
        long    lastSeen = 0;
    }

    private UserChatSetting getSetting(String chatId, String email) {
        String key = "ucs_" + email;
        for (String entry : prefs.getStringSet(key, new LinkedHashSet<>())) {
            String[] p = entry.split("\\|", -1);
            if (p.length >= 5 && p[0].equals(chatId)) {
                UserChatSetting ucs = new UserChatSetting();
                ucs.muted    = "1".equals(p[1]);
                ucs.archived = "1".equals(p[2]);
                ucs.deleted  = "1".equals(p[3]);
                try { ucs.lastSeen = Long.parseLong(p[4]); } catch (Exception ignored) {}
                return ucs;
            }
        }
        return new UserChatSetting();
    }

    private void saveSetting(String chatId, String email, UserChatSetting ucs) {
        String key = "ucs_" + email;
        Set<String> set = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        set.removeIf(entry -> entry.startsWith(chatId + "|"));
        set.add(chatId + "|"
                + (ucs.muted    ? "1" : "0") + "|"
                + (ucs.archived ? "1" : "0") + "|"
                + (ucs.deleted  ? "1" : "0") + "|"
                + ucs.lastSeen);
        prefs.edit().putStringSet(key, set).apply();
    }
}
