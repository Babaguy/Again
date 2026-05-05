package com.example.again;

import android.util.Base64;
import java.nio.charset.StandardCharsets;

public class Ad {
    private String id;
    private String ownerUsername;
    private String ownerEmail;
    private String productName;
    private String condition;        // "Second-hand" | "As new" | "Unopened" | "Other"
    private String deliveryArea;
    private boolean pickup;
    private boolean shipping;
    private double price;
    private int imageCount;          // 0-3
    private long timestamp;          // creation time (ms)
    private int ageValue;            // 0 = not specified
    private String ageUnit;          // "months" | "years" | ""
    private long editedTimestamp;    // 0 = never edited
    private boolean isSold;          // true once marked sold
    private String description;      // up to 1000 chars, may be empty

    public Ad() { ageUnit = ""; description = ""; }

    // Full constructor (create mode)
    public Ad(String id, String ownerUsername, String ownerEmail,
              String productName, String condition,
              String deliveryArea, boolean pickup, boolean shipping,
              double price, int imageCount, long timestamp,
              int ageValue, String ageUnit, String description) {
        this.id = id;
        this.ownerUsername = ownerUsername;
        this.ownerEmail = ownerEmail;
        this.productName = productName;
        this.condition = condition;
        this.deliveryArea = deliveryArea;
        this.pickup = pickup;
        this.shipping = shipping;
        this.price = price;
        this.imageCount = imageCount;
        this.timestamp = timestamp;
        this.ageValue = ageValue;
        this.ageUnit = ageUnit != null ? ageUnit : "";
        this.editedTimestamp = 0;
        this.isSold = false;
        this.description = description != null ? description : "";
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public String getId()              { return id; }
    public String getOwnerUsername()   { return ownerUsername; }
    public String getOwnerEmail()      { return ownerEmail; }
    public String getProductName()     { return productName; }
    public String getCondition()       { return condition; }
    public String getDeliveryArea()    { return deliveryArea; }
    public boolean isPickup()          { return pickup; }
    public boolean isShipping()        { return shipping; }
    public double getPrice()           { return price; }
    public int getImageCount()         { return imageCount; }
    public long getTimestamp()         { return timestamp; }
    public int getAgeValue()           { return ageValue; }
    public String getAgeUnit()         { return ageUnit != null ? ageUnit : ""; }
    public long getEditedTimestamp()   { return editedTimestamp; }
    public boolean isSold()            { return isSold; }
    public String getDescription()     { return description != null ? description : ""; }

    public void setImageCount(int c)         { imageCount = c; }
    public void setEditedTimestamp(long t)   { editedTimestamp = t; }
    public void setSold(boolean sold)        { isSold = sold; }
    public void setDescription(String d)     { description = d != null ? d : ""; }

    /** Human-readable delivery options, e.g. "Pickup, Shipping" */
    public String getDeliveryOptions() {
        if (pickup && shipping) return "Pickup, Shipping";
        if (pickup)   return "Pickup only";
        if (shipping) return "Shipping only";
        return "Not specified";
    }

    /** Human-readable age, e.g. "2 years" or "" if not set. */
    public String getAgeDisplay() {
        if (ageValue <= 0 || ageUnit == null || ageUnit.isEmpty()) return "";
        return ageValue + " " + ageUnit;
    }

    // ─── Serialisation ────────────────────────────────────────────────────────

    /** Serialise to pipe-delimited string for SharedPreferences. */
    public String serialize() {
        // Description is Base64-encoded to safely handle any characters
        String encodedDesc = (description != null && !description.isEmpty())
                ? Base64.encodeToString(description.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP)
                : "";
        return id + "|" + ownerUsername + "|" + ownerEmail + "|"
                + productName.replace("|", "\\|") + "|"
                + condition + "|"
                + deliveryArea.replace("|", "\\|") + "|"
                + (pickup ? "1" : "0") + "|"
                + (shipping ? "1" : "0") + "|"
                + price + "|"
                + imageCount + "|"
                + timestamp + "|"
                + ageValue + "|"
                + (ageUnit != null ? ageUnit : "") + "|"
                + editedTimestamp + "|"
                + (isSold ? "1" : "0") + "|"
                + encodedDesc;
    }

    /** Deserialise — backward compatible down to 11-field format. */
    public static Ad deserialize(String s) {
        String[] parts = s.split("(?<!\\\\)\\|", -1);
        if (parts.length < 11) return null;
        try {
            Ad ad = new Ad();
            ad.id              = parts[0];
            ad.ownerUsername   = parts[1];
            ad.ownerEmail      = parts[2];
            ad.productName     = parts[3].replace("\\|", "|");
            ad.condition       = parts[4];
            ad.deliveryArea    = parts[5].replace("\\|", "|");
            ad.pickup          = "1".equals(parts[6]);
            ad.shipping        = "1".equals(parts[7]);
            ad.price           = Double.parseDouble(parts[8]);
            ad.imageCount      = Integer.parseInt(parts[9]);
            ad.timestamp       = Long.parseLong(parts[10]);
            ad.ageValue        = parts.length > 11 ? Integer.parseInt(parts[11]) : 0;
            ad.ageUnit         = parts.length > 12 ? parts[12] : "";
            ad.editedTimestamp = parts.length > 13 ? Long.parseLong(parts[13]) : 0;
            ad.isSold          = parts.length > 14 && "1".equals(parts[14]);
            if (parts.length > 15 && !parts[15].isEmpty()) {
                ad.description = new String(Base64.decode(parts[15], Base64.NO_WRAP),
                        StandardCharsets.UTF_8);
            } else {
                ad.description = "";
            }
            return ad;
        } catch (Exception e) {
            return null;
        }
    }
}
