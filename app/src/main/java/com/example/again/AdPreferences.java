package com.example.again;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AdPreferences {

    private static final String PREFS_NAME = "again_ads";
    private static final String KEY_ADS    = "ads";

    private final Context context;
    private final SharedPreferences prefs;

    public AdPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.prefs   = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Save a new ad ────────────────────────────────────────────────────────

    public Ad saveAd(String ownerUsername, String ownerEmail,
                     String productName, String condition,
                     String deliveryArea, boolean pickup, boolean shipping,
                     double price, int ageValue, String ageUnit,
                     String description, List<Uri> imageUris) {

        String id = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        int savedImages = 0;
        for (int i = 0; i < Math.min(imageUris.size(), 3); i++) {
            Uri uri = imageUris.get(i);
            if (uri != null && saveImageFromUri(uri, id, i)) savedImages++;
        }

        Ad ad = new Ad(id, ownerUsername, ownerEmail, productName, condition,
                deliveryArea, pickup, shipping, price, savedImages, timestamp,
                ageValue, ageUnit, description);

        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_ADS, new LinkedHashSet<>()));
        existing.add(ad.serialize());
        prefs.edit().putStringSet(KEY_ADS, existing).apply();
        return ad;
    }

    // ─── Load ads ─────────────────────────────────────────────────────────────

    /** All ads for the marketplace feed — excludes sold items. */
    public List<Ad> getAllAds() {
        Set<String> raw = prefs.getStringSet(KEY_ADS, new LinkedHashSet<>());
        List<Ad> ads = new ArrayList<>();
        for (String s : raw) {
            Ad ad = Ad.deserialize(s);
            if (ad != null && !ad.isSold()) ads.add(ad);
        }
        ads.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return ads;
    }

    /** All ads belonging to an owner, including sold ones (for the seller's own list). */
    public List<Ad> getAdsByOwner(String ownerEmail) {
        Set<String> raw = prefs.getStringSet(KEY_ADS, new LinkedHashSet<>());
        List<Ad> result = new ArrayList<>();
        for (String s : raw) {
            Ad ad = Ad.deserialize(s);
            if (ad != null && ad.getOwnerEmail().equalsIgnoreCase(ownerEmail)) result.add(ad);
        }
        result.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return result;
    }

    /** Fetch a single ad by id (returns null if not found). */
    public Ad getAdById(String adId) {
        for (String s : prefs.getStringSet(KEY_ADS, new LinkedHashSet<>())) {
            Ad ad = Ad.deserialize(s);
            if (ad != null && ad.getId().equals(adId)) return ad;
        }
        return null;
    }

    // ─── Delete an ad ─────────────────────────────────────────────────────────

    public void deleteAd(String adId) {
        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_ADS, new LinkedHashSet<>()));
        existing.removeIf(s -> { Ad a = Ad.deserialize(s); return a != null && a.getId().equals(adId); });
        prefs.edit().putStringSet(KEY_ADS, existing).apply();
        deleteAdImages(adId);
    }

    // ─── Mark as sold ─────────────────────────────────────────────────────────

    public void markSold(String adId) {
        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_ADS, new LinkedHashSet<>()));
        String oldEntry = null;
        Ad target = null;
        for (String s : existing) {
            Ad a = Ad.deserialize(s);
            if (a != null && a.getId().equals(adId)) { oldEntry = s; target = a; break; }
        }
        if (target == null || oldEntry == null) return;
        target.setSold(true);
        existing.remove(oldEntry);
        existing.add(target.serialize());
        prefs.edit().putStringSet(KEY_ADS, existing).apply();
    }

    // ─── Unmark as sold ──────────────────────────────────────────────────────

    public void unmarkSold(String adId) {
        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_ADS, new LinkedHashSet<>()));
        String oldEntry = null;
        Ad target = null;
        for (String s : existing) {
            Ad a = Ad.deserialize(s);
            if (a != null && a.getId().equals(adId)) { oldEntry = s; target = a; break; }
        }
        if (target == null || oldEntry == null) return;
        target.setSold(false);
        existing.remove(oldEntry);
        existing.add(target.serialize());
        prefs.edit().putStringSet(KEY_ADS, existing).apply();
    }

    // ─── Update an existing ad ────────────────────────────────────────────────

    public Ad updateAd(Ad existingAd, String productName, String condition,
                       String deliveryArea, boolean pickup, boolean shipping,
                       double price, int ageValue, String ageUnit,
                       String description, List<Uri> imageUris) {

        File dir = new File(context.getFilesDir(), "ads");
        if (!dir.exists()) dir.mkdirs();

        // Pass 1: write to temp files (sources still alive)
        File[] tmpFiles = new File[3];
        int savedImages = 0;
        for (int i = 0; i < Math.min(imageUris.size(), 3); i++) {
            Uri uri = imageUris.get(i);
            if (uri == null) continue;
            File tmp = new File(dir, existingAd.getId() + "_tmp_" + i + ".jpg");
            if (saveImageToFile(uri, tmp)) {
                tmpFiles[i] = tmp;
                savedImages++;
            }
        }

        // Pass 2: delete old originals
        deleteAdImages(existingAd.getId());

        // Pass 3: rename temps → finals
        for (int i = 0; i < 3; i++) {
            if (tmpFiles[i] != null) {
                File dest = new File(dir, existingAd.getId() + "_" + i + ".jpg");
                //noinspection ResultOfMethodCallIgnored
                tmpFiles[i].renameTo(dest);
            }
        }

        Ad updated = new Ad(existingAd.getId(), existingAd.getOwnerUsername(),
                existingAd.getOwnerEmail(), productName, condition,
                deliveryArea, pickup, shipping, price, savedImages,
                existingAd.getTimestamp(), ageValue, ageUnit, description);
        updated.setEditedTimestamp(System.currentTimeMillis());

        Set<String> existing = new LinkedHashSet<>(prefs.getStringSet(KEY_ADS, new LinkedHashSet<>()));
        existing.removeIf(s -> { Ad a = Ad.deserialize(s); return a != null && a.getId().equals(existingAd.getId()); });
        existing.add(updated.serialize());
        prefs.edit().putStringSet(KEY_ADS, existing).apply();
        return updated;
    }

    // ─── Image helpers ────────────────────────────────────────────────────────

    public File getImageFile(String adId, int index) {
        File dir = new File(context.getFilesDir(), "ads");
        File f = new File(dir, adId + "_" + index + ".jpg");
        return f.exists() ? f : null;
    }

    public Bitmap loadImage(String adId, int index) {
        File f = getImageFile(adId, index);
        if (f == null) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private boolean saveImageFromUri(Uri uri, String adId, int index) {
        File dir = new File(context.getFilesDir(), "ads");
        if (!dir.exists()) dir.mkdirs();
        return saveImageToFile(uri, new File(dir, adId + "_" + index + ".jpg"));
    }

    private boolean saveImageToFile(Uri uri, File dest) {
        try {
            InputStream is;
            if ("file".equals(uri.getScheme())) {
                is = new FileInputStream(uri.getPath());
            } else {
                is = context.getContentResolver().openInputStream(uri);
            }
            if (is == null) return false;
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            if (bmp == null) return false;
            bmp = scaleBitmap(bmp, 800);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            }
            bmp.recycle();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void deleteAdImages(String adId) {
        File dir = new File(context.getFilesDir(), "ads");
        for (int i = 0; i < 3; i++) {
            File f = new File(dir, adId + "_" + i + ".jpg");
            if (f.exists()) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    private Bitmap scaleBitmap(Bitmap src, int maxWidth) {
        if (src.getWidth() <= maxWidth) return src;
        float ratio = (float) maxWidth / src.getWidth();
        int h = Math.round(src.getHeight() * ratio);
        return Bitmap.createScaledBitmap(src, maxWidth, h, true);
    }
}
