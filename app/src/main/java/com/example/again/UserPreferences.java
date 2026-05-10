package com.example.again;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public class UserPreferences {
    private static final String PREF_NAME = "again_users";
    private static final String KEY_USERS = "users";
    private static final String KEY_LOGGED_IN = "logged_in_user";

    private final SharedPreferences prefs;

    public UserPreferences(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // User data stored as "username|email|password|phone"
    public boolean registerUser(String username, String email, String password, String phone) {
        Set<String> existing = new HashSet<>(prefs.getStringSet(KEY_USERS, new HashSet<>()));
        for (String entry : existing) {
            String[] parts = entry.split("\\|", -1);
            if (parts[1].equalsIgnoreCase(email)) return false; // email already taken
        }
        existing.add(username + "|" + email + "|" + password + "|" + phone);
        prefs.edit().putStringSet(KEY_USERS, existing).apply();
        return true;
    }

    // Returns user parts [username, email, password, phone] or null if invalid
    public String[] loginUser(String email, String password) {
        Set<String> users = prefs.getStringSet(KEY_USERS, new HashSet<>());
        for (String entry : users) {
            String[] parts = entry.split("\\|", -1);
            if (parts[1].equalsIgnoreCase(email) && parts[2].equals(password)) {
                prefs.edit().putString(KEY_LOGGED_IN, entry).apply();
                return parts;
            }
        }
        return null;
    }

    public boolean isLoggedIn() {
        return prefs.getString(KEY_LOGGED_IN, null) != null;
    }

    // Returns [username, email, password, phone] for the current user, or null
    public String[] getLoggedInUser() {
        String entry = prefs.getString(KEY_LOGGED_IN, null);
        if (entry == null) return null;
        return entry.split("\\|", -1);
    }

    public void logout() {
        prefs.edit().remove(KEY_LOGGED_IN).apply();
    }

    /**
     * Update username and phone for the account identified by email.
     * Email and password are kept unchanged.
     * Also refreshes the logged-in session string.
     * Returns false if the account was not found.
     */
    public boolean updateUser(String email, String newUsername, String newPhone) {
        Set<String> users = new HashSet<>(prefs.getStringSet(KEY_USERS, new HashSet<>()));
        String oldEntry = null;
        String password = "";
        for (String entry : users) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length >= 3 && parts[1].equalsIgnoreCase(email)) {
                oldEntry = entry;
                password = parts[2];
                break;
            }
        }
        if (oldEntry == null) return false;
        users.remove(oldEntry);
        String newEntry = newUsername + "|" + email + "|" + password + "|" + newPhone;
        users.add(newEntry);
        prefs.edit()
                .putStringSet(KEY_USERS, users)
                .putString(KEY_LOGGED_IN, newEntry)
                .apply();
        return true;
    }

    /**
     * Change the password for the account identified by email.
     * Also refreshes the logged-in session string.
     * Returns false if the account was not found.
     */
    public boolean changePassword(String email, String newPassword) {
        Set<String> users = new HashSet<>(prefs.getStringSet(KEY_USERS, new HashSet<>()));
        String oldEntry = null;
        String username = "", phone = "";
        for (String entry : users) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length >= 3 && parts[1].equalsIgnoreCase(email)) {
                oldEntry = entry;
                username = parts[0];
                phone    = parts.length >= 4 ? parts[3] : "";
                break;
            }
        }
        if (oldEntry == null) return false;
        users.remove(oldEntry);
        String newEntry = username + "|" + email + "|" + newPassword + "|" + phone;
        users.add(newEntry);
        prefs.edit()
                .putStringSet(KEY_USERS, users)
                .putString(KEY_LOGGED_IN, newEntry)
                .apply();
        return true;
    }
}
