package com.example.again;

/**
 * Represents a conversation thread between two users about a specific ad.
 *
 * Serialisation uses ASCII Record-Separator (U+001E) as field delimiter.
 * Format (10 fields):
 *   chatId RS buyerEmail RS sellerEmail RS buyerName RS sellerName
 *   RS adId RS adTitle RS adPrice RS lastMsgText RS lastMsgTimestamp
 */
public class Chat {

    private static final char RS = '';   // ASCII Record Separator

    private String chatId;
    private String buyerEmail;
    private String sellerEmail;
    private String buyerName;
    private String sellerName;
    private String adId;
    private String adTitle;
    private double adPrice;
    private String  lastMessageText;
    private long    lastMessageTimestamp;
    private boolean disabled;

    public Chat() {}

    public Chat(String chatId,
                String buyerEmail, String buyerName,
                String sellerEmail, String sellerName,
                String adId, String adTitle, double adPrice) {
        this.chatId               = chatId;
        this.buyerEmail           = buyerEmail;
        this.buyerName            = buyerName;
        this.sellerEmail          = sellerEmail;
        this.sellerName           = sellerName;
        this.adId                 = adId;
        this.adTitle              = adTitle != null ? adTitle : "";
        this.adPrice              = adPrice;
        this.lastMessageText      = "";
        this.lastMessageTimestamp = System.currentTimeMillis();
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getChatId()            { return chatId; }
    public String getBuyerEmail()        { return buyerEmail; }
    public String getSellerEmail()       { return sellerEmail; }
    public String getBuyerName()         { return buyerName; }
    public String getSellerName()        { return sellerName; }
    public String getAdId()              { return adId; }
    public String getAdTitle()           { return adTitle; }
    public double getAdPrice()           { return adPrice; }
    public String getLastMessageText()   { return lastMessageText; }
    public long   getLastMessageTimestamp() { return lastMessageTimestamp; }

    public boolean isDisabled()                     { return disabled; }
    public void setDisabled(boolean d)             { disabled = d; }
    public void setAdTitle(String t)               { adTitle = t != null ? t : ""; }
    public void setLastMessageText(String t)       { lastMessageText = t != null ? t : ""; }
    public void setLastMessageTimestamp(long ts)   { lastMessageTimestamp = ts; }

    /** The name of the other participant (not myEmail). */
    public String getOtherUserName(String myEmail) {
        if (myEmail == null) return sellerName;
        return myEmail.equalsIgnoreCase(buyerEmail) ? sellerName : buyerName;
    }

    /** The email of the other participant (not myEmail). */
    public String getOtherUserEmail(String myEmail) {
        if (myEmail == null) return sellerEmail;
        return myEmail.equalsIgnoreCase(buyerEmail) ? sellerEmail : buyerEmail;
    }

    /** True if this user is a participant in the chat. */
    public boolean involves(String email) {
        return email != null &&
               (email.equalsIgnoreCase(buyerEmail) || email.equalsIgnoreCase(sellerEmail));
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    public String serialize() {
        // Strip stray RS characters from free-text fields
        String safeAdTitle  = adTitle  != null ? adTitle.replace(String.valueOf(RS), "")  : "";
        String safeLastMsg  = lastMessageText != null
                ? lastMessageText.replace(String.valueOf(RS), "") : "";
        String safeBuyerN   = buyerName  != null ? buyerName.replace(String.valueOf(RS), "")  : "";
        String safeSellerN  = sellerName != null ? sellerName.replace(String.valueOf(RS), "") : "";

        return chatId + RS + buyerEmail + RS + sellerEmail
                + RS + safeBuyerN + RS + safeSellerN
                + RS + adId + RS + safeAdTitle + RS + adPrice
                + RS + safeLastMsg + RS + lastMessageTimestamp;
    }

    public static Chat deserialize(String s) {
        String[] parts = s.split(String.valueOf(RS), -1);
        if (parts.length < 10) return null;
        try {
            Chat c = new Chat();
            c.chatId               = parts[0];
            c.buyerEmail           = parts[1];
            c.sellerEmail          = parts[2];
            c.buyerName            = parts[3];
            c.sellerName           = parts[4];
            c.adId                 = parts[5];
            c.adTitle              = parts[6];
            c.adPrice              = Double.parseDouble(parts[7]);
            c.lastMessageText      = parts[8];
            c.lastMessageTimestamp = Long.parseLong(parts[9]);
            return c;
        } catch (Exception e) {
            return null;
        }
    }
}
