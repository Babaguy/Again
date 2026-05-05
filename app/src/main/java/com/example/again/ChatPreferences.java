package com.example.again;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent storage for chats and messages using SharedPreferences.
 *
 * Keys in prefs "again_chats":
 *   "chats"           → StringSet of Chat.serialize() strings
 *   "msgs_{chatId}"   → StringSet of Message.serialize() strings
 *   "ucs_{email}"     → StringSet of per-user-per-chat settings:
 *                          "{chatId}|{muted}|{archived}|{deleted}|{lastSeen}"
 *   "notif_{email}"   → StringSet of "{chatId}|{lastNotifiedTs}" (notification tracking)
 */
public class ChatPreferences {

    private static final String PREFS_NAME  = "again_chats";
    private static final String KEY_CHATS   = "chats";

    // ASCII RS used inside Chat/Message — safe to use plain | here since these
    // are different keys and we control the format entirely.
    private static final char US_SETTINGS = '|';

    private final SharedPreferences prefs;

    public ChatPreferences(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Chat creation ────────────────────────────────────────────────────────

    /**
     * Find an existing chat between these two users about this ad, or null.
     */
    public Chat findChat(String buyerEmail, String sellerEmail, String adId) {
        for (Chat c : loadAllChats()) {
            if (c.getAdId().equals(adId)
                    && c.getBuyerEmail().equalsIgnoreCase(buyerEmail)
                    && c.getSellerEmail().equalsIgnoreCase(sellerEmail)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Start a new chat. Creates the Chat, sends a product-card message and then
     * an "I'm interested" text message from the buyer. Returns the chatId.
     */
    public String startChat(String buyerEmail, String buyerName,
                            String sellerEmail, String sellerName,
                            String adId, String adTitle, double adPrice) {
        String chatId = UUID.randomUUID().toString();
        Chat chat = new Chat(chatId, buyerEmail, buyerName,
                sellerEmail, sellerName, adId, adTitle, adPrice);

        // Product-card message
        String cardText = Message.makeProductCardText(
                adTitle, String.format(Locale.US, "₪%.2f", adPrice));
        sendMessageInternal(chat, new Message(
                UUID.randomUUID().toString(), chatId, buyerEmail,
                cardText, System.currentTimeMillis(), Message.TYPE_PRODUCT_CARD));

        // "I'm interested" text
        sendMessageInternal(chat, new Message(
                UUID.randomUUID().toString(), chatId, buyerEmail,
                "I'm interested", System.currentTimeMillis() + 1, Message.TYPE_TEXT));

        saveChat(chat);
        return chatId;
    }

    // ─── Send a message ───────────────────────────────────────────────────────

    public void sendMessage(String chatId, String senderEmail, String text) {
        Chat chat = getChatById(chatId);
        if (chat == null) return;
        Message msg = new Message(UUID.randomUUID().toString(), chatId,
                senderEmail, text, System.currentTimeMillis(), Message.TYPE_TEXT);
        sendMessageInternal(chat, msg);
        saveChat(chat);

        // If the recipient had deleted the chat, restore it so they see the new message
        String otherEmail = chat.getOtherUserEmail(senderEmail);
        if (otherEmail != null && isDeleted(chatId, otherEmail)) {
            unDeleteChat(chatId, otherEmail);
        }
    }

    private void sendMessageInternal(Chat chat, Message msg) {
        // Persist message
        String key = "msgs_" + chat.getChatId();
        Set<String> msgs = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        msgs.add(msg.serialize());
        prefs.edit().putStringSet(key, msgs).apply();

        // Update chat's last-message preview
        String preview;
        if (msg.isDeleted()) {
            preview = "Message deleted";
        } else if (Message.TYPE_PRODUCT_CARD.equals(msg.getType())) {
            preview = "📦 " + chat.getAdTitle();
        } else if (Message.TYPE_SYSTEM.equals(msg.getType())) {
            preview = "🗑 " + msg.getText();
        } else {
            preview = msg.getText();
        }
        chat.setLastMessageText(preview);
        chat.setLastMessageTimestamp(msg.getTimestamp());
    }

    // ─── Load messages ────────────────────────────────────────────────────────

    public List<Message> getMessages(String chatId) {
        String key = "msgs_" + chatId;
        Set<String> raw = prefs.getStringSet(key, new LinkedHashSet<>());
        List<Message> list = new ArrayList<>();
        for (String s : raw) {
            Message m = Message.deserialize(s);
            if (m != null) list.add(m);
        }
        list.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));
        return list;
    }

    public Message getLastMessage(String chatId) {
        List<Message> msgs = getMessages(chatId);
        return msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);
    }

    // ─── Delete / edit messages ───────────────────────────────────────────────

    /** Mark a message as deleted (only by sender). Returns true on success. */
    public boolean deleteMessage(String chatId, String msgId, String senderEmail) {
        String key = "msgs_" + chatId;
        Set<String> raw = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        String oldSerialized = null;
        Message target = null;
        for (String s : raw) {
            Message m = Message.deserialize(s);
            if (m != null && m.getMsgId().equals(msgId)
                    && m.getSenderEmail().equalsIgnoreCase(senderEmail)) {
                oldSerialized = s;
                target = m;
                break;
            }
        }
        if (target == null) return false;
        target.setDeleted(true);
        raw.remove(oldSerialized);
        raw.add(target.serialize());
        prefs.edit().putStringSet(key, raw).apply();

        // Update chat last-message if needed
        Chat chat = getChatById(chatId);
        if (chat != null) {
            Message last = getLastMessage(chatId);
            if (last != null) {
                chat.setLastMessageText(last.isDeleted() ? "Message deleted" : last.getText());
                saveChat(chat);
            }
        }
        return true;
    }

    /**
     * Edit a message's text (only by sender, only within 1 hour). Returns true on success.
     */
    public boolean editMessage(String chatId, String msgId, String senderEmail, String newText) {
        String key = "msgs_" + chatId;
        Set<String> raw = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        String oldSerialized = null;
        Message target = null;
        for (String s : raw) {
            Message m = Message.deserialize(s);
            if (m != null && m.getMsgId().equals(msgId)
                    && m.getSenderEmail().equalsIgnoreCase(senderEmail)
                    && m.canEdit()) {
                oldSerialized = s;
                target = m;
                break;
            }
        }
        if (target == null) return false;
        target.setText(newText);
        target.setEditedTimestamp(System.currentTimeMillis());
        raw.remove(oldSerialized);
        raw.add(target.serialize());
        prefs.edit().putStringSet(key, raw).apply();

        // Update chat last-message if it was the last one
        Chat chat = getChatById(chatId);
        if (chat != null) {
            Message last = getLastMessage(chatId);
            if (last != null && last.getMsgId().equals(msgId)) {
                chat.setLastMessageText(newText);
                saveChat(chat);
            }
        }
        return true;
    }

    // ─── Chat list ────────────────────────────────────────────────────────────

    /** All chats the user participates in, not deleted by them. Sorted newest-first. */
    public List<Chat> getChatsForUser(String email) {
        List<Chat> result = new ArrayList<>();
        for (Chat c : loadAllChats()) {
            if (c.involves(email) && !isDeleted(c.getChatId(), email)) {
                result.add(c);
            }
        }
        result.sort((a, b) -> Long.compare(b.getLastMessageTimestamp(), a.getLastMessageTimestamp()));
        return result;
    }

    /** Non-archived chats. */
    public List<Chat> getActiveChatsForUser(String email) {
        List<Chat> result = new ArrayList<>();
        for (Chat c : getChatsForUser(email)) {
            if (!isArchived(c.getChatId(), email)) result.add(c);
        }
        return result;
    }

    /** Archived chats. */
    public List<Chat> getArchivedChatsForUser(String email) {
        List<Chat> result = new ArrayList<>();
        for (Chat c : getChatsForUser(email)) {
            if (isArchived(c.getChatId(), email)) result.add(c);
        }
        return result;
    }

    public Chat getChatById(String chatId) {
        for (Chat c : loadAllChats()) {
            if (c.getChatId().equals(chatId)) return c;
        }
        return null;
    }

    // ─── Per-user chat settings ───────────────────────────────────────────────

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

    /**
     * Update the stored adTitle in every chat that references this adId.
     * Call this whenever an ad's name is changed so chat headers stay in sync.
     */
    public void updateAdTitleInChats(String adId, String newTitle) {
        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_CHATS, new LinkedHashSet<>()));
        Set<String> updated  = new LinkedHashSet<>();
        boolean changed = false;
        for (String s : existing) {
            Chat c = Chat.deserialize(s);
            if (c != null && c.getAdId().equals(adId)) {
                c.setAdTitle(newTitle != null ? newTitle : "");
                updated.add(c.serialize());
                changed = true;
            } else {
                updated.add(s);
            }
        }
        if (changed) prefs.edit().putStringSet(KEY_CHATS, updated).apply();
    }

    /**
     * Send a system message to every non-deleted chat about a specific ad,
     * notifying participants that the item has been sold.
     */
    public void sendSoldNotification(String adId, String sellerEmail, String productName) {
        String text = "🏷️ \"" + productName + "\" has been marked as sold.";
        for (Chat chat : loadAllChats()) {
            if (!chat.getAdId().equals(adId)) continue;
            if (!chat.getSellerEmail().equalsIgnoreCase(sellerEmail)) continue;
            if (isDeleted(chat.getChatId(), sellerEmail)) continue;

            Message sysMsg = new Message(
                    UUID.randomUUID().toString(), chat.getChatId(), sellerEmail,
                    text, System.currentTimeMillis(), Message.TYPE_SYSTEM);
            sendMessageInternal(chat, sysMsg);
            saveChat(chat);

            // Restore for the buyer if they had previously deleted the chat
            String buyerEmail = chat.getBuyerEmail();
            if (isDeleted(chat.getChatId(), buyerEmail)) {
                unDeleteChat(chat.getChatId(), buyerEmail);
            }
        }
    }

    /**
     * Send a system message to every non-deleted chat about a specific ad,
     * notifying participants that the item is available again.
     */
    public void sendUnsoldNotification(String adId, String sellerEmail, String productName) {
        String text = "✅ \"" + productName + "\" is available again.";
        for (Chat chat : loadAllChats()) {
            if (!chat.getAdId().equals(adId)) continue;
            if (!chat.getSellerEmail().equalsIgnoreCase(sellerEmail)) continue;
            if (isDeleted(chat.getChatId(), sellerEmail)) continue;

            Message sysMsg = new Message(
                    UUID.randomUUID().toString(), chat.getChatId(), sellerEmail,
                    text, System.currentTimeMillis(), Message.TYPE_SYSTEM);
            sendMessageInternal(chat, sysMsg);
            saveChat(chat);

            // Restore the buyer's view if they had previously deleted the chat
            String buyerEmail = chat.getBuyerEmail();
            if (isDeleted(chat.getChatId(), buyerEmail)) {
                unDeleteChat(chat.getChatId(), buyerEmail);
            }
        }
    }

    /** Restore a previously deleted chat back into the user's active list. */
    public void unDeleteChat(String chatId, String email) {
        UserChatSetting s = getSetting(chatId, email);
        s.deleted  = false;
        s.archived = false;   // bring it back to the active list, not the archive
        saveSetting(chatId, email, s);
    }

    /** Soft-delete: removes the chat from this user's view and posts a system notice. */
    public void deleteChat(String chatId, String email, String displayName) {
        // Post a system message so the other person sees what happened
        Chat chat = getChatById(chatId);
        if (chat != null) {
            String notice = displayName + " deleted this conversation";
            Message sysMsg = new Message(
                    UUID.randomUUID().toString(), chatId, email,
                    notice, System.currentTimeMillis(), Message.TYPE_SYSTEM);
            sendMessageInternal(chat, sysMsg);
            saveChat(chat);
        }
        UserChatSetting s = getSetting(chatId, email);
        s.deleted = true;
        saveSetting(chatId, email, s);
    }

    public boolean isMuted(String chatId, String email) {
        return getSetting(chatId, email).muted;
    }

    public boolean isArchived(String chatId, String email) {
        return getSetting(chatId, email).archived;
    }

    public boolean isDeleted(String chatId, String email) {
        return getSetting(chatId, email).deleted;
    }

    // ─── Read / unread tracking ───────────────────────────────────────────────

    public void markChatRead(String chatId, String email) {
        UserChatSetting s = getSetting(chatId, email);
        s.lastSeen = System.currentTimeMillis();
        saveSetting(chatId, email, s);
    }

    /**
     * Count messages in this chat that were sent by someone other than email
     * after that user's lastSeen timestamp.
     */
    public int getUnreadCount(String chatId, String email) {
        long lastSeen = getSetting(chatId, email).lastSeen;
        int count = 0;
        for (Message m : getMessages(chatId)) {
            if (!m.getSenderEmail().equalsIgnoreCase(email)
                    && m.getTimestamp() > lastSeen
                    && !m.isDeleted()
                    && !Message.TYPE_SYSTEM.equals(m.getType())) {
                count++;
            }
        }
        return count;
    }

    public int getTotalUnreadCount(String email) {
        int total = 0;
        for (Chat c : getChatsForUser(email)) {
            if (!isMuted(c.getChatId(), email)) {
                total += getUnreadCount(c.getChatId(), email);
            }
        }
        return total;
    }

    // ─── Notification tracking ────────────────────────────────────────────────

    /** Returns the timestamp of the last message we already fired a notification for. */
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

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<Chat> loadAllChats() {
        Set<String> raw = prefs.getStringSet(KEY_CHATS, new LinkedHashSet<>());
        List<Chat> list = new ArrayList<>();
        for (String s : raw) {
            Chat c = Chat.deserialize(s);
            if (c != null) list.add(c);
        }
        return list;
    }

    private void saveChat(Chat chat) {
        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_CHATS, new LinkedHashSet<>()));
        // Remove old entry for this chatId, then add updated
        existing.removeIf(s -> {
            Chat c = Chat.deserialize(s);
            return c != null && c.getChatId().equals(chat.getChatId());
        });
        existing.add(chat.serialize());
        prefs.edit().putStringSet(KEY_CHATS, existing).apply();
    }

    // Per-user per-chat settings — stored in "ucs_{email}" StringSet
    // Each entry: "{chatId}|{muted:0/1}|{archived:0/1}|{deleted:0/1}|{lastSeen}"

    private static class UserChatSetting {
        boolean muted    = false;
        boolean archived = false;
        boolean deleted  = false;
        long    lastSeen = 0;
    }

    private UserChatSetting getSetting(String chatId, String email) {
        String key = "ucs_" + email;
        for (String s : prefs.getStringSet(key, new LinkedHashSet<>())) {
            String[] p = s.split("\\|", -1);
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
        set.removeIf(s -> s.startsWith(chatId + "|"));
        set.add(chatId + "|" + (ucs.muted ? "1" : "0")
                + "|" + (ucs.archived ? "1" : "0")
                + "|" + (ucs.deleted ? "1" : "0")
                + "|" + ucs.lastSeen);
        prefs.edit().putStringSet(key, set).apply();
    }
}
