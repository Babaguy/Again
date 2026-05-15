package com.example.again;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

        // Password display row: Firebase does not expose passwords, so hide it
        View etCurrentPasswordDisplay = view.findViewById(R.id.etCurrentPasswordDisplay);
        View btnRevealPassword        = view.findViewById(R.id.btnRevealPassword);
        if (etCurrentPasswordDisplay != null) etCurrentPasswordDisplay.setVisibility(View.GONE);
        if (btnRevealPassword        != null) btnRevealPassword.setVisibility(View.GONE);
        // Also hide the parent LinearLayout that wraps both (if it only contains these two)
        if (etCurrentPasswordDisplay != null
                && etCurrentPasswordDisplay.getParent() instanceof android.view.ViewGroup) {
            android.view.ViewGroup parent =
                    (android.view.ViewGroup) etCurrentPasswordDisplay.getParent();
            if (parent != null) parent.setVisibility(View.GONE);
        }

        // Change Password
        MaterialButton btnChangePassword = view.findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog(userPrefs));

        // Logout
        btnLogout.setOnClickListener(v -> {
            userPrefs.logout();
            if (listener != null) listener.onLogout();
        });

        // Delete Account
        MaterialButton btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount);
        btnDeleteAccount.setOnClickListener(v -> {
            // Build a custom dialog view with a warning message + confirmation checkbox
            android.widget.LinearLayout dialogLayout = new android.widget.LinearLayout(requireContext());
            dialogLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = dpToPx(20);
            dialogLayout.setPadding(pad, pad / 2, pad, 0);

            android.widget.TextView tvWarning = new android.widget.TextView(requireContext());
            tvWarning.setText("This will permanently delete your account and all your data. This cannot be undone.");
            tvWarning.setTextColor(0xFFB292E7);
            tvWarning.setTextSize(14);
            dialogLayout.addView(tvWarning);

            android.widget.CheckBox cbConfirm = new android.widget.CheckBox(requireContext());
            cbConfirm.setText("I understand this is permanent");
            cbConfirm.setTextColor(0xFFE6DCF6);
            cbConfirm.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF8361C5));
            android.widget.LinearLayout.LayoutParams cbParams =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            cbParams.topMargin = dpToPx(16);
            cbConfirm.setLayoutParams(cbParams);
            dialogLayout.addView(cbConfirm);

            androidx.appcompat.app.AlertDialog dialog =
                    new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                            .setTitle("Delete Account?")
                            .setView(dialogLayout)
                            .setPositiveButton("Delete", (d, which) -> {
                                userPrefs.deleteAccount(new UserPreferences.Callback() {
                                    @Override public void onSuccess() {
                                        if (!isAdded()) return;
                                        if (listener != null) listener.onLogout();
                                    }
                                    @Override public void onFailure(String error) {
                                        if (!isAdded()) return;
                                        Toast.makeText(getContext(),
                                                "Could not delete account", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();

            // Disable the Delete button until checkbox is ticked
            android.widget.Button btnDelete = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btnDelete.setEnabled(false);
            btnDelete.setAlpha(0.4f);
            cbConfirm.setOnCheckedChangeListener((cb, isChecked) -> {
                btnDelete.setEnabled(isChecked);
                btnDelete.setAlpha(isChecked ? 1f : 0.4f);
            });
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

    // ── Change Password ───────────────────────────────────────────────────────

    private void showChangePasswordDialog(UserPreferences userPrefs) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null);

        EditText etNew     = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirm = dialogView.findViewById(R.id.etConfirmPassword);
        ImageView toggleNew     = dialogView.findViewById(R.id.btnToggleNew);
        ImageView toggleConfirm = dialogView.findViewById(R.id.btnToggleConfirm);

        // Eye toggles inside dialog
        final boolean[] newVisible     = {false};
        final boolean[] confirmVisible = {false};

        toggleNew.setOnClickListener(v -> {
            newVisible[0] = !newVisible[0];
            etNew.setTransformationMethod(newVisible[0]
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etNew.setSelection(etNew.getText().length());
            toggleNew.setImageResource(newVisible[0]
                    ? R.drawable.ic_eye_off : R.drawable.ic_eye_open);
        });

        toggleConfirm.setOnClickListener(v -> {
            confirmVisible[0] = !confirmVisible[0];
            etConfirm.setTransformationMethod(confirmVisible[0]
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            etConfirm.setSelection(etConfirm.getText().length());
            toggleConfirm.setImageResource(confirmVisible[0]
                    ? R.drawable.ic_eye_off : R.drawable.ic_eye_open);
        });

        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newPass     = etNew.getText().toString().trim();
                    String confirmPass = etConfirm.getText().toString().trim();

                    if (newPass.isEmpty()) {
                        Toast.makeText(getContext(), "Password cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    userPrefs.changePasswordAsync(newPass, new UserPreferences.Callback() {
                        @Override public void onSuccess() {
                            if (!isAdded()) return;
                            Toast.makeText(getContext(), "Password updated!", Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onFailure(String error) {
                            if (!isAdded()) return;
                            Toast.makeText(getContext(), "Could not update password",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
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

        userPrefs.updateUser(currentEmail, newUsername, newPhone, new UserPreferences.Callback() {
            @Override public void onSuccess() {
                if (!isAdded()) return;
                tvProfileInitial.setText(String.valueOf(newUsername.charAt(0)).toUpperCase());
                Toast.makeText(getContext(), "Profile updated!", Toast.LENGTH_SHORT).show();
                exitEditMode(false);
            }
            @Override public void onFailure(String error) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not save changes", Toast.LENGTH_SHORT).show();
                exitEditMode(false);
            }
        });
    }
}
