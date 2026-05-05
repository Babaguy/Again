package com.example.again;

/**
 * A single chat message.
 *
 * Serialisation uses ASCII Record-Separator (U+001E) as field delimiter and
 * ASCII Unit-Separator (U+001F) to join product-card title/price.
 *
 * Format: msgId RS chatId RS senderEmail RS text RS timestamp RS isDeleted RS editedTs RS type
 *
 * For TYPE_PRODUCT_CARD the text field is: adTitle US adPriceString
 */
public class Message {

    public static final String TYPE_TEXT         = "T";
    public static final String TYPE_PRODUCT_CARD = "P";
    public static final String TYPE_SYSTEM       = "S";
    public static final String TYPE_DATE         = "D";  // synthetic, never persisted

    private static final char RS = '';  // ASCII Record Separator  (field delimiter)
    private static final char US = '';  // ASCII Unit Separator    (product-card sub-delimiter)

    private String msgId;
    private String chatId;
    private String senderEmail;
    private String text;
    private long   timestamp;
    private boolean isDeleted;
    private long   editedTimestamp;
    private String type;

    public Message() {}

    public Message(String msgId, String chatId, String senderEmail,
                   String text, long timestamp, String type) {
        this.msgId         = msgId;
        this.chatId        = chatId;
        this.senderEmail   = senderEmail;
        this.text          = text != null ? text : "";
        this.timestamp     = timestamp;
        this.isDeleted     = false;
        this.editedTimestamp = 0;
        this.type          = type;
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getMsgId()           { return msgId; }
    public String getChatId()          { return chatId; }
    public String getSenderEmail()     { return senderEmail; }
    public String getText()            { return text; }
    public void   setText(String t)    { text = t != null ? t : ""; }
    public long   getTimestamp()       { return timestamp; }
    public boolean isDeleted()         { return isDeleted; }
    public void   setDeleted(boolean d){ isDeleted = d; }
    public long   getEditedTimestamp() { return editedTimestamp; }
    public void   setEditedTimestamp(long t) { editedTimestamp = t; }
    public String getType()            { return type; }

    /** True if the message can still be edited (own message, not deleted, within 1 hour). */
    public boolean canEdit() {
        return !isDeleted && System.currentTimeMillis() - timestamp <= 3_600_000L;
    }

    // ── Product-card helpers ─────────────────────────────────────────────────

    /** Title portion of a product-card message. */
    public String getProductTitle() {
        if (!TYPE_PRODUCT_CARD.equals(type) || text == null) return "";
        int sep = text.indexOf(US);
        return sep >= 0 ? text.substring(0, sep) : text;
    }

    /** Price string portion of a product-card message (already formatted). */
    public String getProductPrice() {
        if (!TYPE_PRODUCT_CARD.equals(type) || text == null) return "";
        int sep = text.indexOf(US);
        return sep >= 0 ? text.substring(sep + 1) : "";
    }

    /** Build the text field for a product-card message. */
    public static String makeProductCardText(String adTitle, String priceFormatted) {
        // Strip any stray US characters from user-supplied strings
        String safeTitle = adTitle != null ? adTitle.replace(String.valueOf(US), "") : "";
        String safePrice = priceFormatted != null ? priceFormatted.replace(String.valueOf(US), "") : "";
        return safeTitle + US + safePrice;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    public String serialize() {
        // Strip any accidental RS characters from text so the delimiter stays clean
        String safeText = text != null ? text.replace(String.valueOf(RS), "") : "";
        return msgId + RS + chatId + RS + senderEmail + RS + safeText
                + RS + timestamp
                + RS + (isDeleted ? "1" : "0")
                + RS + editedTimestamp
                + RS + type;
    }

    public static Message deserialize(String s) {
        String[] parts = s.split(String.valueOf(RS), -1);
        if (parts.length < 8) return null;
        try {
            Message m = new Message();
            m.msgId           = parts[0];
            m.chatId          = parts[1];
            m.senderEmail     = parts[2];
            m.text            = parts[3];
            m.timestamp       = Long.parseLong(parts[4]);
            m.isDeleted       = "1".equals(parts[5]);
            m.editedTimestamp = Long.parseLong(parts[6]);
            m.type            = parts[7];
            return m;
        } catch (Exception e) {
            return null;
        }
    }
}
