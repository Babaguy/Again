package com.example.again;

import android.content.Context;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;

public class SignUpFragment extends Fragment {

    public interface SignUpListener {
        void onSignUpSuccess();
        void onNavigateToLogin();
    }

    private SignUpListener listener;
    private boolean passVisible    = false;
    private boolean confirmVisible = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SignUpListener) {
            listener = (SignUpListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_signup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText etUsername = view.findViewById(R.id.etSignUpUsername);
        EditText etGmail    = view.findViewById(R.id.etSignUpGmail);
        EditText etPassword = view.findViewById(R.id.etSignUpPassword);
        EditText etConfirm  = view.findViewById(R.id.etSignUpConfirm);
        EditText etPhone    = view.findViewById(R.id.etSignUpPhone);
        ImageView ivEyePass    = view.findViewById(R.id.ivSignUpEyePass);
        ImageView ivEyeConfirm = view.findViewById(R.id.ivSignUpEyeConfirm);
        MaterialButton btnSignUp = view.findViewById(R.id.btnSignUp);
        TextView tvGoLogin  = view.findViewById(R.id.tvGoToLogin);
        ImageView btnBack   = view.findViewById(R.id.btnSignUpBack);

        // Eye toggles
        ivEyePass.setOnClickListener(v -> {
            passVisible = !passVisible;
            etPassword.setTransformationMethod(passVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            ivEyePass.setImageResource(passVisible ? R.drawable.ic_eye_open : R.drawable.ic_eye_off);
            etPassword.setSelection(etPassword.getText().length());
        });

        ivEyeConfirm.setOnClickListener(v -> {
            confirmVisible = !confirmVisible;
            etConfirm.setTransformationMethod(confirmVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            ivEyeConfirm.setImageResource(confirmVisible ? R.drawable.ic_eye_open : R.drawable.ic_eye_off);
            etConfirm.setSelection(etConfirm.getText().length());
        });

        // Sign Up
        btnSignUp.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String email    = etGmail.getText().toString().trim();
            String password = etPassword.getText().toString();
            String confirm  = etConfirm.getText().toString();
            String phone    = etPhone.getText().toString().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(getContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            String passError = validatePassword(password);
            if (passError != null) {
                Toast.makeText(getContext(), passError, Toast.LENGTH_LONG).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(getContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSignUp.setEnabled(false);
            btnSignUp.setText("Creating account…");

            new UserPreferences(requireContext()).registerUser(username, email, password, phone,
                    new UserPreferences.Callback() {
                        @Override public void onSuccess() {
                            if (!isAdded()) return;
                            if (listener != null) listener.onSignUpSuccess();
                        }
                        @Override public void onFailure(String error) {
                            if (!isAdded()) return;
                            btnSignUp.setEnabled(true);
                            btnSignUp.setText("Create Account");
                            String msg = error != null && error.contains("email address is already")
                                    ? "This email is already registered"
                                    : (error != null ? error : "Registration failed");
                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        tvGoLogin.setOnClickListener(v -> {
            if (listener != null) listener.onNavigateToLogin();
        });

        btnBack.setOnClickListener(v -> {
            if (listener != null) listener.onNavigateToLogin();
        });
    }

    private String validatePassword(String password) {
        if (password.length() < 8 || password.length() > 25) {
            return "Password must be 8–25 characters long";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one number";
        }
        return null;
    }
}
