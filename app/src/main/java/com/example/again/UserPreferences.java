package com.example.again;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class UserPreferences {

    // ── Callback ──────────────────────────────────────────────────────────────

    public interface Callback {
        void onSuccess();
        void onFailure(String error);
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String COL_USERS = "users";

    /**
     * In-memory cache: [0]=username [1]=email [2]="" (password not exposed) [3]=phone
     * Null until a login / restoreSession completes.
     */
    private static String[] cachedUser = null;

    // ── Firebase ──────────────────────────────────────────────────────────────

    private final Context           appContext;
    private final FirebaseAuth      auth;
    private final FirebaseFirestore db;

    public UserPreferences(Context context) {
        appContext = context.getApplicationContext();
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
    }

    // ── Sync helpers ──────────────────────────────────────────────────────────

    /** True only when Firebase Auth has a current user AND the profile cache is loaded. */
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null && cachedUser != null;
    }

    /** Returns [username, email, "", phone] from memory cache, or null if not loaded. */
    public String[] getLoggedInUser() {
        return cachedUser;
    }

    /** Firebase UID of the current user, or null. */
    public String getUid() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    // ── Register ──────────────────────────────────────────────────────────────

    public void registerUser(String username, String email, String password,
                             String phone, Callback cb) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    String msg = task.getException() != null
                            ? task.getException().getMessage() : "Registration failed";
                    cb.onFailure(msg);
                    return;
                }
                FirebaseUser fu = auth.getCurrentUser();
                if (fu == null) { cb.onFailure("Unknown error"); return; }

                Map<String, Object> data = new HashMap<>();
                data.put("username", username);
                data.put("email", email);
                data.put("phone", phone);

                db.collection(COL_USERS).document(fu.getUid())
                    .set(data)
                    .addOnSuccessListener(v -> {
                        cachedUser = new String[]{username, email, "", phone};
                        cb.onSuccess();
                    })
                    .addOnFailureListener(e -> {
                        // Firestore write failed — roll back the Auth account so the
                        // email doesn't get permanently blocked for re-registration.
                        fu.delete();
                        cb.onFailure(e.getMessage());
                    });
            });
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public void loginUser(String email, String password, Callback cb) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    cb.onFailure("Incorrect email or password");
                    return;
                }
                FirebaseUser fu = auth.getCurrentUser();
                if (fu == null) { cb.onFailure("Unknown error"); return; }
                loadProfile(fu.getUid(), cb);
            });
    }

    /**
     * If Firebase Auth already has a signed-in user (app restart), load the
     * profile from Firestore into the memory cache.  Call from MainActivity.onCreate().
     */
    public void restoreSession(Callback cb) {
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null)         { cb.onFailure("Not signed in"); return; }
        if (cachedUser != null) { cb.onSuccess();                return; }  // already loaded
        loadProfile(fu.getUid(), cb);
    }

    private void loadProfile(String uid, Callback cb) {
        db.collection(COL_USERS).document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String username = doc.getString("username");
                    String email    = doc.getString("email");
                    String phone    = doc.getString("phone");
                    cachedUser = new String[]{
                            username != null ? username : "",
                            email    != null ? email    : "",
                            "",
                            phone    != null ? phone    : ""
                    };
                    cb.onSuccess();
                } else {
                    cb.onFailure("Profile not found");
                }
            })
            .addOnFailureListener(e -> cb.onFailure(e.getMessage()));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    public void logout() {
        auth.signOut();
        cachedUser = null;
    }

    // ── Update profile ────────────────────────────────────────────────────────

    public void updateUser(String email, String newUsername, String newPhone, Callback cb) {
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) { cb.onFailure("Not signed in"); return; }

        Map<String, Object> updates = new HashMap<>();
        updates.put("username", newUsername);
        updates.put("phone", newPhone);

        db.collection(COL_USERS).document(fu.getUid())
            .update(updates)
            .addOnSuccessListener(v -> {
                if (cachedUser != null) {
                    cachedUser[0] = newUsername;
                    cachedUser[3] = newPhone;
                }
                cb.onSuccess();
            })
            .addOnFailureListener(e -> cb.onFailure(e.getMessage()));
    }

    // ── Delete account ────────────────────────────────────────────────────────

    /**
     * Permanently deletes the account in four steps:
     *  1. Send "user deleted their account" system message in every chat they were part of.
     *  2. Delete all ads they posted.
     *  3. Delete their Firestore profile document.
     *  4. Delete their Firebase Auth account.
     * The in-memory cache is cleared on success.
     */
    public void deleteAccount(Callback cb) {
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) { cb.onFailure("Not signed in"); return; }
        String uid = fu.getUid();

        String email       = cachedUser != null ? cachedUser[1]
                           : (fu.getEmail() != null ? fu.getEmail() : "");
        String displayName = cachedUser != null && !cachedUser[0].isEmpty()
                           ? cachedUser[0] : email;

        ChatPreferences chatPrefs = new ChatPreferences(appContext);
        AdPreferences   adPrefs   = new AdPreferences(appContext);

        // Step 1 — notify chat partners + disable/purge chats
        chatPrefs.handleUserAccountDeletion(email, displayName, () -> {

            // Step 2 — delete all ads
            adPrefs.deleteAdsByOwner(email, () -> {

                // Step 3 — delete Firestore profile
                db.collection(COL_USERS).document(uid).delete()
                    .addOnSuccessListener(v ->

                        // Step 4 — delete the Firebase Auth account while still signed in
                        fu.delete()
                            .addOnCompleteListener(task -> {
                                // Step 5 — sign out and navigate to login regardless of
                                // whether Auth deletion succeeded (Firestore data is already gone)
                                cachedUser = null;
                                auth.signOut();
                                cb.onSuccess();
                            }))
                    .addOnFailureListener(e -> cb.onFailure(e.getMessage()));
            });
        });
    }

    // ── Change password ───────────────────────────────────────────────────────

    public void changePasswordAsync(String newPassword, Callback cb) {
        FirebaseUser fu = auth.getCurrentUser();
        if (fu == null) { cb.onFailure("Not signed in"); return; }

        fu.updatePassword(newPassword)
            .addOnSuccessListener(v -> cb.onSuccess())
            .addOnFailureListener(e -> cb.onFailure(e.getMessage()));
    }
}
