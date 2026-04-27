package com.example.again;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

public class MainActivity extends AppCompatActivity {

    private ImageButton btnLeft, btnCenter, btnRight;
    private int currentTab = 1;
    private FrameLayout splashOverlay;
    private boolean splashDismissed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        // Add splash overlay on top of everything
        showSplashOverlay();

        // Search bar expand/collapse on focus
        ConstraintLayout searchBarLayout = findViewById(R.id.search_bar);
        EditText editTextSearch = findViewById(R.id.editTextSearch);
        // Collapse when Done is pressed on the keyboard
        editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                editTextSearch.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editTextSearch.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        editTextSearch.setOnFocusChangeListener((v, hasFocus) -> {
            ChangeBounds transition = new ChangeBounds();
            transition.setDuration(250);
            TransitionManager.beginDelayedTransition(searchBarLayout, transition);
            ConstraintSet cs = new ConstraintSet();
            cs.clone(searchBarLayout);
            if (hasFocus) {
                cs.setVisibility(R.id.btnPlus, ConstraintSet.GONE);
                cs.setVisibility(R.id.btnAcc, ConstraintSet.GONE);
                cs.setMargin(R.id.searchContainer, ConstraintSet.START, 0);
                cs.setMargin(R.id.searchContainer, ConstraintSet.END, 0);
            } else {
                cs.setVisibility(R.id.btnPlus, ConstraintSet.VISIBLE);
                cs.setVisibility(R.id.btnAcc, ConstraintSet.VISIBLE);
                cs.setMargin(R.id.searchContainer, ConstraintSet.START, dpToPx(8));
                cs.setMargin(R.id.searchContainer, ConstraintSet.END, dpToPx(8));
            }
            cs.applyTo(searchBarLayout);
        });

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

    private void showSplashOverlay() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.parseColor("#21103E"));
        splashOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Again");
        title.setTextColor(Color.parseColor("#E6DCF6"));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 52);
        title.setLetterSpacing(0.12f);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        title.setLayoutParams(params);

        splashOverlay.addView(title);

        ViewGroup root = (ViewGroup) getWindow().getDecorView();
        root.addView(splashOverlay);
    }

    private void dismissSplash() {
        if (splashDismissed || splashOverlay == null) return;
        splashDismissed = true;

        splashOverlay.post(() -> {
            float targetY = -splashOverlay.getHeight();
            ObjectAnimator animator = ObjectAnimator.ofFloat(splashOverlay, "translationY", 0f, targetY);
            animator.setDuration(550);
            animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ViewGroup root = (ViewGroup) getWindow().getDecorView();
                    root.removeView(splashOverlay);
                    splashOverlay = null;
                }
            });
            animator.start();
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Intercept the first tap to dismiss the splash
        if (!splashDismissed && splashOverlay != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dismissSplash();
            }
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                int[] location = new int[2];
                focused.getLocationOnScreen(location);
                float x = event.getRawX(), y = event.getRawY();
                boolean outsideX = x < location[0] || x > location[0] + focused.getWidth();
                boolean outsideY = y < location[1] || y > location[1] + focused.getHeight();
                if (outsideX || outsideY) {
                    focused.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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