package com.example.again;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity
        implements LoginFragment.LoginListener,
                   SignUpFragment.SignUpListener,
                   ProfileFragment.ProfileListener {

    private ImageButton btnLeft, btnCenter, btnRight;
    private MaterialButton btnAcc;
    private int currentTab = 1;

    private FrameLayout splashOverlay;
    private boolean splashDismissed = false;

    private FrameLayout authContainer;
    private boolean authShowing = false;

    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        showSplashOverlay();

        authContainer = findViewById(R.id.auth_container);
        btnAcc = findViewById(R.id.btnAcc);

        // Restore icon state if already logged in
        updateAccountIcon(new UserPreferences(this).isLoggedIn());

        // Handle back press (replaces deprecated onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (authShowing) {
                    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getSupportFragmentManager().popBackStack();
                    } else {
                        closeAuth(false, null);
                    }
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        // Account button
        btnAcc.setOnClickListener(v -> {
            if (new UserPreferences(this).isLoggedIn()) {
                openAuth(new ProfileFragment(), false);
            } else {
                openAuth(new LoginFragment(), false);
            }
        });

        // Swipe gesture detector
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 100;
            private static final int SWIPE_MIN_VELOCITY = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) < Math.abs(diffY)) return false;
                if (Math.abs(diffX) < SWIPE_MIN_DISTANCE || Math.abs(vX) < SWIPE_MIN_VELOCITY) return false;

                if (diffX < 0) {
                    // Swipe left → go right
                    if (currentTab == 0) {
                        replaceFragment(new HomeFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
                        highlightButton(btnCenter);
                        currentTab = 1;
                        return true;
                    } else if (currentTab == 1) {
                        replaceFragment(new RightFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
                        highlightButton(btnRight);
                        currentTab = 2;
                        return true;
                    }
                } else {
                    // Swipe right → go left
                    if (currentTab == 2) {
                        replaceFragment(new HomeFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
                        highlightButton(btnCenter);
                        currentTab = 1;
                        return true;
                    } else if (currentTab == 1) {
                        replaceFragment(new LeftFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
                        highlightButton(btnLeft);
                        currentTab = 0;
                        return true;
                    }
                }
                return false;
            }
        });

        // Search bar
        ConstraintLayout searchBarLayout = findViewById(R.id.search_bar);
        EditText editTextSearch = findViewById(R.id.editTextSearch);

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

        // Navigation buttons
        btnLeft   = findViewById(R.id.btn_left);
        btnCenter = findViewById(R.id.btn_center);
        btnRight  = findViewById(R.id.btn_right);

        replaceFragment(new HomeFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
        highlightButton(btnCenter);

        btnLeft.setOnClickListener(v -> {
            if (currentTab == 0) return;
            replaceFragment(new LeftFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
            highlightButton(btnLeft);
            currentTab = 0;
        });

        btnCenter.setOnClickListener(v -> {
            if (currentTab == 1) return;
            if (currentTab == 0) {
                replaceFragment(new HomeFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
            } else {
                replaceFragment(new HomeFragment(), R.anim.slide_in_left, R.anim.slide_out_right);
            }
            highlightButton(btnCenter);
            currentTab = 1;
        });

        btnRight.setOnClickListener(v -> {
            if (currentTab == 2) return;
            replaceFragment(new RightFragment(), R.anim.slide_in_right, R.anim.slide_out_left);
            highlightButton(btnRight);
            currentTab = 2;
        });
    }

    // ─── Auth container helpers ──────────────────────────────────────────────

    private void openAuth(Fragment fragment, boolean fromLeft) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.auth_container, fragment)
                .commit();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        authContainer.setTranslationX(fromLeft ? -screenW : screenW);
        authContainer.setVisibility(View.VISIBLE);
        authContainer.animate()
                .translationX(0)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        authShowing = true;
    }

    private void closeAuth(boolean slideLeft, Runnable onEnd) {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        authContainer.animate()
                .translationX(slideLeft ? -screenW : screenW)
                .setDuration(350)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    authContainer.setVisibility(View.GONE);
                    authContainer.setTranslationX(0);
                    authShowing = false;
                    // Clear fragments from the auth container
                    getSupportFragmentManager()
                            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    if (onEnd != null) onEnd.run();
                })
                .start();
    }

    // ─── LoginListener ───────────────────────────────────────────────────────

    @Override
    public void onLoginSuccess() {
        updateAccountIcon(true);
        closeAuth(true, () ->
                Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onNavigateToSignUp() {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left,  R.anim.slide_out_right)
                .replace(R.id.auth_container, new SignUpFragment())
                .addToBackStack("signup")
                .commit();
    }

    @Override
    public void onLoginClose() {
        closeAuth(false, null);
    }

    // ─── SignUpListener ───────────────────────────────────────────────────────

    @Override
    public void onSignUpSuccess() {
        // Pop back to Login (plays the pop animations set above: slide_in_left / slide_out_right)
        getSupportFragmentManager().popBackStack();
        new Handler().postDelayed(() ->
                Toast.makeText(this, "Account created! Please sign in.", Toast.LENGTH_LONG).show(),
                400);
    }

    @Override
    public void onNavigateToLogin() {
        getSupportFragmentManager().popBackStack();
    }

    // ─── ProfileListener ─────────────────────────────────────────────────────

    @Override
    public void onLogout() {
        updateAccountIcon(false);
        closeAuth(false, null);
    }

    @Override
    public void onProfileClose() {
        closeAuth(false, null);
    }

    // ─── Account icon ────────────────────────────────────────────────────────

    private void updateAccountIcon(boolean loggedIn) {
        if (btnAcc != null) {
            btnAcc.setIconResource(loggedIn ? R.drawable.ic_account : R.drawable.ic_account_outline);
        }
    }

    // ─── Touch / gestures ────────────────────────────────────────────────────

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Splash takes priority
        if (!splashDismissed && splashOverlay != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) dismissSplash();
            return true;
        }

        // When auth is open, don't let swipes through
        if (authShowing) return super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                int[] loc = new int[2];
                focused.getLocationOnScreen(loc);
                float x = event.getRawX(), y = event.getRawY();
                if (x < loc[0] || x > loc[0] + focused.getWidth()
                        || y < loc[1] || y > loc[1] + focused.getHeight()) {
                    focused.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                }
            }
        }
        gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    // ─── Splash ──────────────────────────────────────────────────────────────

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
        ((ViewGroup) getWindow().getDecorView()).addView(splashOverlay);
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
                    ((ViewGroup) getWindow().getDecorView()).removeView(splashOverlay);
                    splashOverlay = null;
                }
            });
            animator.start();
        });
    }

    // ─── Misc ────────────────────────────────────────────────────────────────

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
        Drawable bg = activeBtn.getBackground();
        if (bg != null) DrawableCompat.setTint(DrawableCompat.wrap(bg), Color.parseColor("#8361C5"));
    }

    private void resetButton(ImageButton btn) {
        Drawable bg = btn.getBackground();
        if (bg != null) DrawableCompat.setTint(DrawableCompat.wrap(bg), Color.parseColor("#B292E7"));
    }
}
