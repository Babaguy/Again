package com.example.again;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

public class ProfileFragment extends Fragment {

    public interface ProfileListener {
        void onLogout();
        void onProfileClose();
    }

    private ProfileListener listener;

    // Views
    private ImageView    btnEditProfile;
    private ImageView    btnCloseProfile;
    private TextView     tvProfileInitial;
    private EditText     etProfileUsername;
    private TextView     tvProfileGmail;
    private EditText     etProfilePhone;
    private MaterialButton btnLogout;

    // State
    private boolean isEditing = false;
    private String  currentEmail = "";

    // Drawables cached for fast toggling
    private Drawable drawablePencil;
    private Drawable drawableCheck;
    // Background for username EditText when in edit mode
    private Drawable inputBackground;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ProfileListener) listener = (ProfileListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        btnEditProfile   = view.findViewById(R.id.btnEditProfile);
        btnCloseProfile  = view.findViewById(R.id.btnCloseProfile);
        tvProfileInitial = view.findViewById(R.id.tvProfileInitial);
        etProfileUsername = view.findViewById(R.id.etProfileUsername);
        tvProfileGmail   = view.findViewById(R.id.tvProfileGmail);
        etProfilePhone   = view.findViewById(R.id.etProfilePhone);
        btnLogout        = view.findViewById(R.id.btnLogout);

        // Cache drawables
        drawablePencil  = ContextCompat.getDrawable(requireContext(), R.drawable.ic_edit);
        drawableCheck   = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check);
        inputBackground = ContextCompat.getDrawable(requireContext(), R.drawable.auth_input_background);

        // Load user data
        UserPreferences userPrefs = new UserPreferences(requireContext());
        String[] user = userPrefs.getLoggedInUser();
        if (user != null) {
            currentEmail = user[1];
            populateFields(user[0], user[1], user[3]);
        }

        // Edit / Save button
        btnEditProfile.setOnClickListener(v -> {
            if (isEditing) {
                saveChanges(userPrefs);
            } else {
                enterEditMode();
            }
        });

        // Close
        btnCloseProfile.setOnClickListener(v -> {
            if (isEditing) exitEditMode(false); // discard on close
            if (listener != null) listener.onProfileClose();
        });

        // Logout
        btnLogout.setOnClickListener(v -> {
            userPrefs.logout();
            if (listener != null) listener.onLogout();
        });
    }

    // ── Populate ──────────────────────────────────────────────────────────────

    private void populateFields(String username, String email, String phone) {
        etProfileUsername.setText(username);
        tvProfileGmail.setText(email);
        etProfilePhone.setText(phone);
        tvProfileInitial.setText(username.isEmpty() ? "?" :
                String.valueOf(username.charAt(0)).toUpperCase());
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private void enterEditMode() {
        isEditing = true;

        // Make username editable
        etProfileUsername.setFocusable(true);
        etProfileUsername.setFocusableInTouchMode(true);
        etProfileUsername.setCursorVisible(true);
        etProfileUsername.setBackground(inputBackground);
        etProfileUsername.requestFocus();

        // Make phone editable
        etProfilePhone.setFocusable(true);
        etProfilePhone.setFocusableInTouchMode(true);
        etProfilePhone.setCursorVisible(true);

        // Swap pencil → check (tinted green-ish purple to signal "save")
        btnEditProfile.setImageDrawable(drawableCheck);
        btnEditProfile.setColorFilter(0xFF8361C5);   // app purple

        // Show soft keyboard
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etProfileUsername, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitEditMode(boolean restoreOriginal) {
        isEditing = false;

        if (restoreOriginal) {
            // Reload from prefs to discard changes
            UserPreferences prefs = new UserPreferences(requireContext());
            String[] user = prefs.getLoggedInUser();
            if (user != null) populateFields(user[0], user[1], user[3]);
        }

        // Lock fields
        etProfileUsername.setFocusable(false);
        etProfileUsername.setFocusableInTouchMode(false);
        etProfileUsername.setCursorVisible(false);
        etProfileUsername.setBackground(null);

        etProfilePhone.setFocusable(false);
        etProfilePhone.setFocusableInTouchMode(false);
        etProfilePhone.setCursorVisible(false);

        // Swap check → pencil
        btnEditProfile.setImageDrawable(drawablePencil);
        btnEditProfile.clearColorFilter();

        // Hide soft keyboard
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private void saveChanges(UserPreferences userPrefs) {
        String newUsername = etProfileUsername.getText().toString().trim();
        String newPhone    = etProfilePhone.getText().toString().trim();

        if (newUsername.isEmpty()) {
            etProfileUsername.setError("Name cannot be empty");
            etProfileUsername.requestFocus();
            return;
        }

        boolean ok = userPrefs.updateUser(currentEmail, newUsername, newPhone);
        if (ok) {
            // Update avatar initial live
            tvProfileInitial.setText(String.valueOf(newUsername.charAt(0)).toUpperCase());
            Toast.makeText(getContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Could not save changes", Toast.LENGTH_SHORT).show();
        }

        exitEditMode(false);
    }
}
