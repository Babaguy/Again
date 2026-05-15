package com.example.again;

import android.content.Context;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
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

public class LoginFragment extends Fragment {

    public interface LoginListener {
        void onLoginSuccess();
        void onNavigateToSignUp();
        void onLoginClose();
    }

    private LoginListener listener;
    private boolean passwordVisible = false;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof LoginListener) {
            listener = (LoginListener) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText etGmail    = view.findViewById(R.id.etLoginGmail);
        EditText etPassword = view.findViewById(R.id.etLoginPassword);
        ImageView ivEye     = view.findViewById(R.id.ivLoginEye);
        MaterialButton btnLogin = view.findViewById(R.id.btnLogin);
        TextView tvSignUp   = view.findViewById(R.id.tvGoToSignUp);
        ImageView btnClose  = view.findViewById(R.id.btnLoginClose);

        // Eye toggle
        ivEye.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                ivEye.setImageResource(R.drawable.ic_eye_open);
            } else {
                etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
                ivEye.setImageResource(R.drawable.ic_eye_off);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        // Sign In
        btnLogin.setOnClickListener(v -> {
            String email    = etGmail.getText().toString().trim();
            String password = etPassword.getText().toString();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in…");

            new UserPreferences(requireContext()).loginUser(email, password,
                    new UserPreferences.Callback() {
                        @Override public void onSuccess() {
                            if (!isAdded()) return;
                            if (listener != null) listener.onLoginSuccess();
                        }
                        @Override public void onFailure(String error) {
                            if (!isAdded()) return;
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Sign In");
                            Toast.makeText(getContext(), "Incorrect email or password",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        tvSignUp.setOnClickListener(v -> {
            if (listener != null) listener.onNavigateToSignUp();
        });

        btnClose.setOnClickListener(v -> {
            if (listener != null) listener.onLoginClose();
        });
    }
}
