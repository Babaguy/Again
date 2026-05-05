package com.example.again;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdDetailFragment extends DialogFragment {

    private static final String ARG_AD = "ad_serialized";

    public static AdDetailFragment newInstance(Ad ad) {
        AdDetailFragment f = new AdDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_AD, ad.serialize());
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.AdDetailDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ad_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        String serialized = getArguments() != null ? getArguments().getString(ARG_AD) : null;
        Ad ad = serialized != null ? Ad.deserialize(serialized) : null;
        if (ad == null) { dismiss(); return; }

        AdPreferences adPrefs = new AdPreferences(requireContext());

        // ── Images ────────────────────────────────────────────────────────────
        List<Bitmap> bitmaps = new ArrayList<>();
        for (int i = 0; i < ad.getImageCount(); i++) {
            Bitmap bmp = adPrefs.loadImage(ad.getId(), i);
            if (bmp != null) bitmaps.add(bmp);
        }

        ViewPager2 vpImages = root.findViewById(R.id.vpImages);
        LinearLayout llDots = root.findViewById(R.id.llDots);
        TextView tvPhotoCount = root.findViewById(R.id.tvDetailPhotoCount);

        if (!bitmaps.isEmpty()) {
            vpImages.setAdapter(new AdImagePagerAdapter(bitmaps, this::showFullscreen));
            buildDots(llDots, bitmaps.size(), 0);

            // Show counter only when there are multiple photos
            if (bitmaps.size() > 1) {
                tvPhotoCount.setVisibility(View.VISIBLE);
                tvPhotoCount.setText("1 / " + bitmaps.size());
            }

            vpImages.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    buildDots(llDots, bitmaps.size(), position);
                    if (bitmaps.size() > 1) {
                        tvPhotoCount.setText((position + 1) + " / " + bitmaps.size());
                    }
                }
            });
        } else {
            // Hide the entire CardView when there are no images
            View cardView = (View) vpImages.getParent().getParent();
            cardView.setVisibility(View.GONE);
            llDots.setVisibility(View.GONE);
        }

        // ── Dates ─────────────────────────────────────────────────────────────
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        TextView tvPostedDate = root.findViewById(R.id.tvDetailPostedDate);
        if (ad.getTimestamp() > 0) {
            tvPostedDate.setText("Posted " + sdf.format(new Date(ad.getTimestamp())));
        } else {
            tvPostedDate.setVisibility(View.GONE);
        }

        TextView tvEditedDate = root.findViewById(R.id.tvDetailEditedDate);
        if (ad.getEditedTimestamp() > 0) {
            tvEditedDate.setText("Last Edited " + sdf.format(new Date(ad.getEditedTimestamp())));
            tvEditedDate.setVisibility(View.VISIBLE);
        }

        // ── Text fields ───────────────────────────────────────────────────────
        setText(root, R.id.tvDetailName, ad.getProductName());
        setText(root, R.id.tvDetailPrice, String.format(Locale.US, "₪%.2f", ad.getPrice()));
        setText(root, R.id.tvDetailCondition, ad.getCondition());
        setText(root, R.id.tvDetailArea,
                ad.getDeliveryArea().isEmpty() ? "Not specified" : ad.getDeliveryArea());
        setText(root, R.id.tvDetailDelivery, ad.getDeliveryOptions());
        setText(root, R.id.tvDetailOwner, ad.getOwnerUsername());

        // ── Item age ──────────────────────────────────────────────────────────
        if (ad.getAgeValue() > 0) {
            root.findViewById(R.id.tvDetailAgeLabel).setVisibility(View.VISIBLE);
            TextView tvAge = root.findViewById(R.id.tvDetailAge);
            tvAge.setVisibility(View.VISIBLE);
            tvAge.setText(ad.getAgeDisplay());
        }

        // ── Description ───────────────────────────────────────────────────────
        if (!ad.getDescription().isEmpty()) {
            root.findViewById(R.id.tvDetailDescLabel).setVisibility(View.VISIBLE);
            TextView tvDesc = root.findViewById(R.id.tvDetailDescription);
            tvDesc.setVisibility(View.VISIBLE);
            tvDesc.setText(ad.getDescription());
        }

        // ── Chat button ───────────────────────────────────────────────────────
        MaterialButton btnChat = root.findViewById(R.id.btnChatSeller);
        UserPreferences up = new UserPreferences(requireContext());

        if (up.isLoggedIn()) {
            String[] me = up.getLoggedInUser();
            String myEmail = me != null ? me[1] : "";
            // Don't show chat button to the ad's own owner
            if (!myEmail.equalsIgnoreCase(ad.getOwnerEmail())) {
                btnChat.setVisibility(View.VISIBLE);
                btnChat.setOnClickListener(v -> startChat(ad, myEmail, me[0]));
            }
        }
        // If not logged in: chat button stays GONE

        // ── Dismiss on dim tap ────────────────────────────────────────────────
        View dimBg = root.findViewById(R.id.dimBackground);
        dimBg.setOnClickListener(v -> dismiss());

        // ── Drag zone → swipe down to dismiss ────────────────────────────────
        View detailSheet    = root.findViewById(R.id.detailSheet);
        View dragHandleZone = root.findViewById(R.id.dragHandleZone);
        final float[] startRawY = {0};

        dragHandleZone.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    startRawY[0] = event.getRawY();
                    // Propagate disallow up through LinearLayout → NestedScrollView
                    // so the NSV never steals subsequent MOVE events.
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE: {
                    float delta = event.getRawY() - startRawY[0];
                    if (delta > 0) {
                        detailSheet.setTranslationY(delta);
                        // Fade the scrim as the sheet is pulled down
                        float progress = Math.min(delta / dpToPx(200), 1f);
                        dimBg.setAlpha(1f - progress * 0.75f);
                    }
                    return true;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    float translation = detailSheet.getTranslationY();
                    if (translation > dpToPx(120)) {
                        // Slide the sheet off the bottom, then dismiss
                        detailSheet.animate()
                                .translationY(detailSheet.getHeight())
                                .setDuration(250)
                                .setInterpolator(new AccelerateInterpolator())
                                .withEndAction(this::dismiss)
                                .start();
                        dimBg.animate().alpha(0f).setDuration(250).start();
                    } else {
                        // Not far enough — spring back
                        detailSheet.animate()
                                .translationY(0)
                                .setDuration(200)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                        dimBg.animate().alpha(1f).setDuration(200).start();
                    }
                    return true;
                }
            }
            return false;
        });
    }

    // ── Chat with seller ──────────────────────────────────────────────────────

    private void startChat(Ad ad, String myEmail, String myName) {
        // Check notification permission first (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).requestNotificationPermission();
                }
            }
        }

        ChatPreferences chatPrefs = new ChatPreferences(requireContext());

        // Re-use existing chat if one already exists
        Chat existing = chatPrefs.findChat(myEmail, ad.getOwnerEmail(), ad.getId());
        String chatId;
        if (existing != null) {
            chatId = existing.getChatId();
            // Un-delete if they had previously deleted it
            if (chatPrefs.isDeleted(chatId, myEmail)) {
                chatPrefs.unDeleteChat(chatId, myEmail);
            }
        } else {
            chatId = chatPrefs.startChat(
                    myEmail, myName,
                    ad.getOwnerEmail(), ad.getOwnerUsername(),
                    ad.getId(), ad.getProductName(), ad.getPrice());
        }

        Chat chat = chatPrefs.getChatById(chatId);
        if (chat == null) {
            Toast.makeText(getContext(), "Could not open chat", Toast.LENGTH_SHORT).show();
            return;
        }

        dismiss();

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openChat(chat, myEmail);
        }
    }

    // ── Dot indicator ─────────────────────────────────────────────────────────

    private void buildDots(LinearLayout llDots, int count, int selected) {
        llDots.removeAllViews();
        if (count <= 1) return;
        int dp4 = dpToPx(4), dp8 = dpToPx(8), dp6 = dpToPx(6);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            int size = (i == selected) ? dp8 : dp6;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(dp4, 0, dp4, 0);
            dot.setLayoutParams(lp);
            android.graphics.drawable.GradientDrawable circle =
                    new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (i == selected) circle.setColor(Color.parseColor("#8361C5"));
            else { circle.setColor(Color.parseColor("#9F8FBC")); circle.setAlpha(130); }
            dot.setBackground(circle);
            llDots.addView(dot);
        }
    }

    // ── Fullscreen image viewer ───────────────────────────────────────────────

    private void showFullscreen(Bitmap bitmap) {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(new ImageView(requireContext()) {{
            setImageBitmap(bitmap);
            setScaleType(ImageView.ScaleType.FIT_CENTER);
            setBackgroundColor(Color.BLACK);
            setOnClickListener(v -> dialog.dismiss());
        }});
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
        dialog.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setText(View root, int id, String text) {
        TextView tv = root.findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
