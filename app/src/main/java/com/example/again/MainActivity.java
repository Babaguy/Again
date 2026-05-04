package com.example.again;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity
        implements LoginFragment.LoginListener,
                   SignUpFragment.SignUpListener,
                   ProfileFragment.ProfileListener,
                   CreateAdFragment.CreateAdListener,
                   ChatFragment.ChatListener {

    private ImageButton btnLeft, btnCenter, btnRight;
    private MaterialButton btnAcc;
    private int currentTab = 1;

    private FrameLayout splashOverlay;
    private boolean splashDismissed = false;

    private FrameLayout authContainer;
    private boolean authShowing = false;

    private FrameLayout createAdContainer;
    private boolean createAdShowing = false;

    private FrameLayout chatContainer;
    private boolean chatShowing = false;

    private GestureDetector gestureDetector;
    private ConstraintLayout searchBarLayout;
    private EditText editTextSearch;
    private boolean searchExpanded = false;

    private LeftFragment  leftRef;
    private HomeFragment  homeRef;
    private RightFragment rightRef;

    // Notification permission launcher (Android 13+)
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        // Let the app handle its own insets so IME insets are dispatched into child views
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Notification setup
        NotificationHelper.createChannel(this);

        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
                    }
                });

        // Request notification permission on first launch (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                android.content.SharedPreferences appPrefs =
                        getSharedPreferences("again_app", MODE_PRIVATE);
                if (!appPrefs.getBoolean("notif_asked", false)) {
                    appPrefs.edit().putBoolean("notif_asked", true).apply();
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }

        showSplashOverlay();

        authContainer     = findViewById(R.id.auth_container);
        createAdContainer = findViewById(R.id.create_ad_container);
        chatContainer     = findViewById(R.id.chat_container);
        btnAcc            = findViewById(R.id.btnAcc);

        updateAccountIcon(new UserPreferences(this).isLoggedIn());

        // Back press handler
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (chatShowing) {
                    closeChat();
                } else if (createAdShowing) {
                    closeCreateAd();
                } else if (authShowing) {
                    if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getSupportFragmentManager().popBackStack();
                    } else {
                        closeAuth(false, null);
                    }
                } else if (searchExpanded) {
                    searchExpanded = false;
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(editTextSearch.getWindowToken(), 0);
                    editTextSearch.clearFocus();
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

        // Plus button
        View btnPlus = findViewById(R.id.btnPlus);
        if (btnPlus != null) {
            btnPlus.setOnClickListener(v -> {
                if (!new UserPreferences(this).isLoggedIn()) {
                    Toast.makeText(this, "Sign in to post an ad", Toast.LENGTH_SHORT).show();
                    openAuth(new LoginFragment(), false);
                    return;
                }
                openCreateAd();
            });
        }

        // Swipe gesture
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 100;
            private static final int SWIPE_MIN_VELOCITY = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) < Math.abs(diffY)) return false;
                if (Math.abs(diffX) < SWIPE_MIN_DISTANCE || Math.abs(vX) < SWIPE_MIN_VELOCITY)
                    return false;

                if (diffX < 0) {
                    if (currentTab == 0) {
                        switchTab(new HomeFragment(), 1, R.anim.slide_in_right, R.anim.slide_out_left);
                        return true;
                    } else if (currentTab == 1) {
                        rightRef = new RightFragment();
                        switchTab(rightRef, 2, R.anim.slide_in_right, R.anim.slide_out_left);
                        return true;
                    }
                } else {
                    if (currentTab == 2) {
                        switchTab(new HomeFragment(), 1, R.anim.slide_in_left, R.anim.slide_out_right);
                        return true;
                    } else if (currentTab == 1) {
                        switchTab(new LeftFragment(), 0, R.anim.slide_in_left, R.anim.slide_out_right);
                        return true;
                    }
                }
                return false;
            }
        });

        // Search bar
        searchBarLayout = findViewById(R.id.search_bar);
        editTextSearch  = findViewById(R.id.editTextSearch);

        editTextSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editTextSearch.getWindowToken(), 0);
                editTextSearch.clearFocus();
                return true;
            }
            return false;
        });

        editTextSearch.setOnFocusChangeListener((v, hasFocus) -> {
            searchExpanded = hasFocus;
            // Set visibility immediately so touch targets are correct before the animation runs
            View btnPlusView = searchBarLayout.findViewById(R.id.btnPlus);
            if (btnPlusView != null) btnPlusView.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
            btnAcc.setVisibility(hasFocus ? View.GONE : View.VISIBLE);

            // Animate only the layout-bounds change (not visibility — already handled above)
            ChangeBounds transition = new ChangeBounds();
            transition.setDuration(250);
            TransitionManager.beginDelayedTransition(searchBarLayout, transition);
            ConstraintSet cs = new ConstraintSet();
            cs.clone(searchBarLayout);
            if (hasFocus) {
                cs.setMargin(R.id.searchContainer, ConstraintSet.START, 0);
                cs.setMargin(R.id.searchContainer, ConstraintSet.END, 0);
            } else {
                cs.setMargin(R.id.searchContainer, ConstraintSet.START, dpToPx(8));
                cs.setMargin(R.id.searchContainer, ConstraintSet.END, dpToPx(8));
            }
            cs.applyTo(searchBarLayout);
        });

        // Navigation buttons
        btnLeft   = findViewById(R.id.btn_left);
        btnCenter = findViewById(R.id.btn_center);
        btnRight  = findViewById(R.id.btn_right);

        homeRef = new HomeFragment();
        replaceFragment(homeRef, R.anim.slide_in_right, R.anim.slide_out_left);
        highlightButton(btnCenter);

        btnLeft.setOnClickListener(v -> {
            if (currentTab == 0) return;
            leftRef = new LeftFragment();
            switchTab(leftRef, 0, R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnCenter.setOnClickListener(v -> {
            if (currentTab == 1) return;
            homeRef = new HomeFragment();
            if (currentTab == 0)
                switchTab(homeRef, 1, R.anim.slide_in_right, R.anim.slide_out_left);
            else
                switchTab(homeRef, 1, R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnRight.setOnClickListener(v -> {
            if (currentTab == 2) return;
            rightRef = new RightFragment();
            switchTab(rightRef, 2, R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // Handle notification tap intent
        handleChatIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleChatIntent(intent);
    }

    private void handleChatIntent(Intent intent) {
        if (intent == null) return;
        String chatId = intent.getStringExtra(NotificationHelper.EXTRA_CHAT_ID);
        if (chatId == null || chatId.isEmpty()) return;

        UserPreferences up = new UserPreferences(this);
        if (!up.isLoggedIn()) return;
        String[] user = up.getLoggedInUser();
        if (user == null) return;

        ChatPreferences chatPrefs = new ChatPreferences(this);
        Chat chat = chatPrefs.getChatById(chatId);
        if (chat == null) return;

        // Navigate to chat tab and open the conversation
        rightRef = new RightFragment();
        switchTab(rightRef, 2, R.anim.slide_in_right, R.anim.slide_out_left);
        new Handler().postDelayed(() -> openChat(chat, user[1]), 300);
    }

    // ── Tab switching ─────────────────────────────────────────────────────────

    private void switchTab(Fragment fragment, int tab, int enterAnim, int exitAnim) {
        if (fragment instanceof LeftFragment)  leftRef  = (LeftFragment) fragment;
        if (fragment instanceof HomeFragment)  homeRef  = (HomeFragment) fragment;
        if (fragment instanceof RightFragment) rightRef = (RightFragment) fragment;
        replaceFragment(fragment, enterAnim, exitAnim);
        highlightButton(tab == 0 ? btnLeft : tab == 1 ? btnCenter : btnRight);
        currentTab = tab;
    }

    // ── Chat overlay ──────────────────────────────────────────────────────────

    public void openChat(Chat chat, String myEmail) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.chat_container, ChatFragment.newInstance(chat.getChatId(), myEmail))
                .commit();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        chatContainer.setTranslationX(screenW);
        chatContainer.setVisibility(View.VISIBLE);
        chatContainer.animate()
                .translationX(0)
                .setDuration(320)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        chatShowing = true;
    }

    private void closeChat() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        chatContainer.animate()
                .translationX(screenW)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    chatContainer.setVisibility(View.GONE);
                    chatContainer.setTranslationX(0);
                    chatShowing = false;
                    getSupportFragmentManager()
                            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    if (rightRef != null) rightRef.refresh();
                })
                .start();
    }

    // ── ChatFragment.ChatListener ─────────────────────────────────────────────

    @Override
    public void onChatClose() {
        closeChat();
    }

    // ── Notification permission (called by AdDetailFragment) ─────────────────

    public void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // ── Create-Ad overlay ─────────────────────────────────────────────────────

    public void openEditAd(Ad ad) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.create_ad_container, CreateAdFragment.newInstance(ad))
                .commit();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        createAdContainer.setTranslationX(-screenW);
        createAdContainer.setVisibility(View.VISIBLE);
        createAdContainer.animate()
                .translationX(0)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        createAdShowing = true;
    }

    private void openCreateAd() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.create_ad_container, new CreateAdFragment())
                .commit();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        createAdContainer.setTranslationX(-screenW);
        createAdContainer.setVisibility(View.VISIBLE);
        createAdContainer.animate()
                .translationX(0)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        createAdShowing = true;
    }

    private void closeCreateAd() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        createAdContainer.animate()
                .translationX(screenW)
                .setDuration(350)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    createAdContainer.setVisibility(View.GONE);
                    createAdContainer.setTranslationX(0);
                    createAdShowing = false;
                    getSupportFragmentManager()
                            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                })
                .start();
    }

    // ── CreateAdListener ──────────────────────────────────────────────────────

    @Override
    public void onAdCreated() {
        closeCreateAd();
        new Handler().postDelayed(() -> {
            if (homeRef != null) homeRef.refresh();
            if (leftRef != null) leftRef.refresh();
        }, 400);
    }

    @Override
    public void onCreateAdClosed() {
        closeCreateAd();
    }

    // ── Auth overlay ──────────────────────────────────────────────────────────

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
                    getSupportFragmentManager()
                            .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    if (onEnd != null) onEnd.run();
                })
                .start();
    }

    // ── LoginListener ─────────────────────────────────────────────────────────

    @Override
    public void onLoginSuccess() {
        updateAccountIcon(true);
        closeAuth(true, () -> {
            Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
            if (leftRef  != null) leftRef.refresh();
            if (rightRef != null) rightRef.refresh();
            // Check for unread messages on login
            String[] user = new UserPreferences(this).getLoggedInUser();
            if (user != null)
                NotificationHelper.checkAndNotifyUnread(this, user[1]);
        });
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

    // ── SignUpListener ────────────────────────────────────────────────────────

    @Override
    public void onSignUpSuccess() {
        getSupportFragmentManager().popBackStack();
        new Handler().postDelayed(() ->
                Toast.makeText(this, "Account created! Please sign in.", Toast.LENGTH_LONG).show(),
                400);
    }

    @Override
    public void onNavigateToLogin() {
        getSupportFragmentManager().popBackStack();
    }

    // ── ProfileListener ───────────────────────────────────────────────────────

    @Override
    public void onLogout() {
        updateAccountIcon(false);
        closeAuth(false, () -> {
            if (leftRef  != null) leftRef.refresh();
            if (rightRef != null) rightRef.refresh();
        });
    }

    @Override
    public void onProfileClose() {
        closeAuth(false, null);
    }

    // ── Account icon ──────────────────────────────────────────────────────────

    private void updateAccountIcon(boolean loggedIn) {
        if (btnAcc != null)
            btnAcc.setIconResource(loggedIn ? R.drawable.ic_account : R.drawable.ic_account_outline);
    }

    // ── Touch / gestures ──────────────────────────────────────────────────────

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!splashDismissed && splashOverlay != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) dismissSplash();
            return true;
        }

        if (authShowing || createAdShowing || chatShowing)
            return super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focused = getCurrentFocus();
            if (focused instanceof EditText) {
                float x = event.getRawX(), y = event.getRawY();
                // Skip edge touches — they are gesture-nav back swipes.
                // Collapsing here would set searchExpanded=false before the
                // OnBackPressedCallback fires, causing the app to exit instead.
                int screenW = getResources().getDisplayMetrics().widthPixels;
                if (x < dpToPx(32) || x > screenW - dpToPx(32)) return super.dispatchTouchEvent(event);
                int[] searchLoc = new int[2];
                searchBarLayout.getLocationOnScreen(searchLoc);
                boolean outsideSearchBar =
                        x < searchLoc[0] || x > searchLoc[0] + searchBarLayout.getWidth()
                     || y < searchLoc[1] || y > searchLoc[1] + searchBarLayout.getHeight();
                if (outsideSearchBar) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                    focused.clearFocus();
                }
            }
        }
        gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    // ── Splash ────────────────────────────────────────────────────────────────

    private void showSplashOverlay() {
        splashOverlay = new FrameLayout(this);
        splashOverlay.setBackgroundColor(Color.parseColor("#21103E"));
        splashOverlay.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("Again");
        title.setTextColor(Color.parseColor("#E6DCF6"));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 52);
        title.setLetterSpacing(0.12f);
        title.setTypeface(android.graphics.Typeface.create(
                "sans-serif-light", android.graphics.Typeface.NORMAL));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
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

    // ── Misc ──────────────────────────────────────────────────────────────────

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void replaceFragment(Fragment fragment, int enterAnim, int exitAnim) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enterAnim, exitAnim,
                        R.anim.slide_in_left, R.anim.slide_out_right)
                .replace(R.id.fragment_main_container, fragment)
                .commit();
    }

    private void highlightButton(ImageButton activeBtn) {
        resetButton(btnLeft);
        resetButton(btnCenter);
        resetButton(btnRight);
        Drawable bg = activeBtn.getBackground();
        if (bg != null)
            DrawableCompat.setTint(DrawableCompat.wrap(bg), Color.parseColor("#8361C5"));
    }

    private void resetButton(ImageButton btn) {
        Drawable bg = btn.getBackground();
        if (bg != null)
            DrawableCompat.setTint(DrawableCompat.wrap(bg), Color.parseColor("#2D1A5C"));
    }
}
