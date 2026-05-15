package com.example.again;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AdPreferences {

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface AdsCallback {
        void onResult(List<Ad> ads);
    }

    public interface DoneCallback {
        void onDone(boolean success, String error);
    }

    // ── Static cache: adId → Ad (with images) ─────────────────────────────────

    private static final Map<String, Ad> adCache = new HashMap<>();

    public static Ad getCachedAd(String adId) {
        return adId != null ? adCache.get(adId) : null;
    }

    /** Instance convenience wrapper around the static cache. */
    public Ad getAdById(String adId) {
        return getCachedAd(adId);
    }

    // ── Firestore ─────────────────────────────────────────────────────────────

    private static final String COL_ADS = "ads";

    private final Context           context;
    private final FirebaseFirestore db;

    public AdPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.db      = FirebaseFirestore.getInstance();
    }

    // ── Save a new ad ─────────────────────────────────────────────────────────

    public void saveAd(String ownerUsername, String ownerEmail, String ownerId,
                       String productName, String condition,
                       String deliveryArea, boolean pickup, boolean shipping,
                       double price, int ageValue, String ageUnit,
                       String description, List<Uri> imageUris, DoneCallback cb) {

        String id        = UUID.randomUUID().toString();
        long   timestamp = System.currentTimeMillis();

        List<String> imageBase64 = encodeUris(imageUris);

        Ad ad = new Ad(id, ownerUsername, ownerEmail, productName, condition,
                deliveryArea, pickup, shipping, price, imageBase64.size(), timestamp,
                ageValue, ageUnit, description);
        ad.setImages(imageBase64);

        Map<String, Object> doc = adToMap(ad);
        doc.put("ownerId", ownerId != null ? ownerId : "");

        db.collection(COL_ADS).document(id)
            .set(doc)
            .addOnSuccessListener(v -> {
                adCache.put(id, ad);
                cb.onDone(true, null);
            })
            .addOnFailureListener(e -> cb.onDone(false, e.getMessage()));
    }

    // ── Get all ads (marketplace feed, unsold, newest first) ──────────────────

    public void getAllAds(AdsCallback cb) {
        db.collection(COL_ADS)
            .get()
            .addOnSuccessListener(qs -> {
                List<Ad> ads = new ArrayList<>();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Ad ad = mapToAd(doc);
                    if (ad != null) {
                        adCache.put(ad.getId(), ad);
                        if (!ad.isSold()) ads.add(ad);
                    }
                }
                ads.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                cb.onResult(ads);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    // ── Get ads owned by a specific user (all, including sold) ────────────────

    public void getAdsByOwner(String ownerEmail, AdsCallback cb) {
        db.collection(COL_ADS)
            .whereEqualTo("ownerEmail", ownerEmail)
            .get()
            .addOnSuccessListener(qs -> {
                List<Ad> ads = new ArrayList<>();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    Ad ad = mapToAd(doc);
                    if (ad != null) {
                        ads.add(ad);
                        adCache.put(ad.getId(), ad);
                    }
                }
                ads.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                cb.onResult(ads);
            })
            .addOnFailureListener(e -> cb.onResult(new ArrayList<>()));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Delete every ad owned by this email in a single batch. Calls onDone when finished. */
    public void deleteAdsByOwner(String ownerEmail, Runnable onDone) {
        db.collection(COL_ADS).whereEqualTo("ownerEmail", ownerEmail).get()
            .addOnSuccessListener(qs -> {
                if (qs.isEmpty()) {
                    if (onDone != null) onDone.run();
                    return;
                }
                WriteBatch batch = db.batch();
                for (DocumentSnapshot doc : qs.getDocuments()) {
                    batch.delete(doc.getReference());
                    adCache.remove(doc.getId());
                }
                batch.commit().addOnCompleteListener(t -> {
                    if (onDone != null) onDone.run();
                });
            })
            .addOnFailureListener(e -> { if (onDone != null) onDone.run(); });
    }

    public void deleteAd(String adId, DoneCallback cb) {
        db.collection(COL_ADS).document(adId)
            .delete()
            .addOnSuccessListener(v -> {
                adCache.remove(adId);
                cb.onDone(true, null);
            })
            .addOnFailureListener(e -> cb.onDone(false, e.getMessage()));
    }

    // ── Mark sold / available ─────────────────────────────────────────────────

    public void markSold(String adId, DoneCallback cb)   { setSold(adId, true,  cb); }
    public void unmarkSold(String adId, DoneCallback cb) { setSold(adId, false, cb); }

    private void setSold(String adId, boolean sold, DoneCallback cb) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("isSold", sold);
        db.collection(COL_ADS).document(adId)
            .update(upd)
            .addOnSuccessListener(v -> {
                Ad cached = adCache.get(adId);
                if (cached != null) cached.setSold(sold);
                cb.onDone(true, null);
            })
            .addOnFailureListener(e -> cb.onDone(false, e.getMessage()));
    }

    // ── Update an existing ad ─────────────────────────────────────────────────

    /**
     * @param newUris         New images per slot (null = slot unchanged or empty)
     * @param existingBase64  Existing base64 per slot (null = removed or replaced by newUri)
     */
    public void updateAd(Ad existingAd,
                         String productName, String condition,
                         String deliveryArea, boolean pickup, boolean shipping,
                         double price, int ageValue, String ageUnit,
                         String description,
                         Uri[] newUris, String[] existingBase64,
                         DoneCallback cb) {

        // Build the final image list from new + kept images
        List<String> finalImages = new ArrayList<>();
        int slots = Math.max(newUris.length, existingBase64 != null ? existingBase64.length : 0);
        for (int i = 0; i < slots; i++) {
            Uri    newUri = i < newUris.length ? newUris[i] : null;
            String oldB64 = existingBase64 != null && i < existingBase64.length
                            ? existingBase64[i] : null;
            if (newUri != null) {
                String b64 = encodeImageUri(newUri);
                if (b64 != null) finalImages.add(b64);
            } else if (oldB64 != null) {
                finalImages.add(oldB64);
            }
        }

        Ad updated = new Ad(existingAd.getId(), existingAd.getOwnerUsername(),
                existingAd.getOwnerEmail(), productName, condition,
                deliveryArea, pickup, shipping, price, finalImages.size(),
                existingAd.getTimestamp(), ageValue, ageUnit, description);
        updated.setEditedTimestamp(System.currentTimeMillis());
        updated.setImages(finalImages);

        Map<String, Object> doc = adToMap(updated);

        db.collection(COL_ADS).document(existingAd.getId())
            .set(doc)
            .addOnSuccessListener(v -> {
                adCache.put(updated.getId(), updated);
                cb.onDone(true, null);
            })
            .addOnFailureListener(e -> cb.onDone(false, e.getMessage()));
    }

    // ── Image helpers (public static so other classes can reuse) ──────────────

    /** Decode a base64 JPEG string to a Bitmap. Returns null on failure. */
    public static Bitmap decodeImage(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<String> encodeUris(List<Uri> uris) {
        List<String> result = new ArrayList<>();
        for (Uri uri : uris) {
            if (uri == null) continue;
            String b64 = encodeImageUri(uri);
            if (b64 != null) result.add(b64);
        }
        return result;
    }

    /** Encode a Uri image: fix EXIF rotation, scale to ≤400px, JPEG quality 55, base64. */
    private String encodeImageUri(Uri uri) {
        try {
            InputStream is;
            if ("file".equals(uri.getScheme())) {
                is = new FileInputStream(uri.getPath());
            } else {
                is = context.getContentResolver().openInputStream(uri);
            }
            if (is == null) return null;
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return null;

            // Correct EXIF orientation before storing
            bmp = fixOrientation(bmp, uri);

            bmp = scaleBitmap(bmp, 400);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 55, baos);
            bmp.recycle();
            return android.util.Base64.encodeToString(
                    baos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Read the EXIF orientation tag and rotate the bitmap to match.
     * BitmapFactory.decodeStream() ignores this tag, so we must apply it manually.
     */
    private Bitmap fixOrientation(Bitmap bmp, Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return bmp;
            ExifInterface exif = new ExifInterface(is);
            is.close();

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int degrees = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:  degrees = 90;  break;
                case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            }
            if (degrees != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(degrees);
                Bitmap rotated = Bitmap.createBitmap(
                        bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
                bmp.recycle();
                return rotated;
            }
        } catch (Exception ignored) {}
        return bmp;
    }

    private Bitmap scaleBitmap(Bitmap src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        float ratio = (float) maxWidth / src.getWidth();
        int h = Math.round(src.getHeight() * ratio);
        return Bitmap.createScaledBitmap(src, maxWidth, h, true);
    }

    // ── Firestore serialization ───────────────────────────────────────────────

    private Map<String, Object> adToMap(Ad ad) {
        Map<String, Object> m = new HashMap<>();
        m.put("id",              ad.getId());
        m.put("ownerUsername",   ad.getOwnerUsername());
        m.put("ownerEmail",      ad.getOwnerEmail());
        m.put("productName",     ad.getProductName());
        m.put("condition",       ad.getCondition());
        m.put("deliveryArea",    ad.getDeliveryArea());
        m.put("pickup",          ad.isPickup());
        m.put("shipping",        ad.isShipping());
        m.put("price",           ad.getPrice());
        m.put("imageCount",      ad.getImageCount());
        m.put("timestamp",       ad.getTimestamp());
        m.put("ageValue",        ad.getAgeValue());
        m.put("ageUnit",         ad.getAgeUnit());
        m.put("editedTimestamp", ad.getEditedTimestamp());
        m.put("isSold",          ad.isSold());
        m.put("description",     ad.getDescription());
        m.put("images",          ad.getImages());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Ad mapToAd(DocumentSnapshot doc) {
        try {
            String id            = doc.getString("id");
            String ownerUsername = doc.getString("ownerUsername");
            String ownerEmail    = doc.getString("ownerEmail");
            String productName   = doc.getString("productName");
            String condition     = doc.getString("condition");
            String deliveryArea  = doc.getString("deliveryArea");
            boolean pickup       = Boolean.TRUE.equals(doc.getBoolean("pickup"));
            boolean shipping     = Boolean.TRUE.equals(doc.getBoolean("shipping"));
            double price         = doc.getDouble("price")        != null ? doc.getDouble("price")  : 0.0;
            int imageCount       = doc.getLong("imageCount")     != null ? doc.getLong("imageCount").intValue() : 0;
            long timestamp       = doc.getLong("timestamp")      != null ? doc.getLong("timestamp") : 0L;
            int ageValue         = doc.getLong("ageValue")       != null ? doc.getLong("ageValue").intValue() : 0;
            String ageUnit       = doc.getString("ageUnit");
            long editedTimestamp = doc.getLong("editedTimestamp") != null ? doc.getLong("editedTimestamp") : 0L;
            boolean isSold       = Boolean.TRUE.equals(doc.getBoolean("isSold"));
            String description   = doc.getString("description");
            List<String> images  = (List<String>) doc.get("images");

            if (id == null) id = doc.getId();

            Ad ad = new Ad(
                    id,
                    ownerUsername   != null ? ownerUsername : "",
                    ownerEmail      != null ? ownerEmail    : "",
                    productName     != null ? productName   : "",
                    condition       != null ? condition     : "",
                    deliveryArea    != null ? deliveryArea  : "",
                    pickup, shipping, price, imageCount, timestamp,
                    ageValue,
                    ageUnit         != null ? ageUnit       : "",
                    description     != null ? description   : "");
            ad.setEditedTimestamp(editedTimestamp);
            ad.setSold(isSold);
            if (images != null) ad.setImages(images);
            return ad;
        } catch (Exception e) {
            return null;
        }
    }
}
