package com.example.again;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnLeft, btnCenter, btnRight;
    private int currentTab = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        // 1. Initialize Buttons
        btnLeft = findViewById(R.id.btn_left);
        btnCenter = findViewById(R.id.btn_center);
        btnRight = findViewById(R.id.btn_right);

        // 2. Set Default Fragment (Home/Center)
        replaceFragment(new HomeFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
        highlightButton(btnCenter);

        // 3. Setup Listeners
        btnLeft.setOnClickListener(v -> {
            if (currentTab == 0) return; // Do nothing if already on Left

            // Moving from 1 or 2 to 0 (Lower index -> Slide Left)
            replaceFragment(new LeftFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
            highlightButton(btnLeft);
            currentTab = 0;
        });

        btnCenter.setOnClickListener(v -> {
            if (currentTab == 1) return; // Do nothing if already on Center

            if (currentTab == 0) {
                // Moving from 0 to 1 (Higher index -> Slide Right)
                replaceFragment(new HomeFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
            } else {
                // Moving from 2 to 1 (Lower index -> Slide Left)
                replaceFragment(new HomeFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
            }
            highlightButton(btnCenter);
            currentTab = 1;
        });

        btnRight.setOnClickListener(v -> {
            if (currentTab == 2) return; // Do nothing if already on Right

            // Moving from 0 or 1 to 2 (Higher index -> Slide Right)
            replaceFragment(new RightFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
            highlightButton(btnRight);
            currentTab = 2;
        });
    }

    private void replaceFragment(Fragment fragment, int enterAnim, int exitAnim) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enterAnim, exitAnim, R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.fragment_main_container, fragment)
                .commit();
    }

    private void highlightButton(ImageButton activeBtn) {
        resetButton(btnLeft);
        resetButton(btnCenter);
        resetButton(btnRight);


        // 2. Tint the Vector without losing the path/radius
        Drawable background = activeBtn.getBackground();
        if (background != null) {
            Drawable wrappedDrawable = DrawableCompat.wrap(background);
            DrawableCompat.setTint(wrappedDrawable, Color.parseColor("#8361C5"));
        }
    }

    private void resetButton(ImageButton btn) {

        Drawable background = btn.getBackground();
        if (background != null) {
            Drawable wrappedDrawable = DrawableCompat.wrap(background);
            // Reset to your original light purple
            DrawableCompat.setTint(wrappedDrawable, Color.parseColor("#B292E7"));
        }
    }

}