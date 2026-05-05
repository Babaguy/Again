package com.example.again;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.example.again.ChatPreferences;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CreateAdFragment extends Fragment {

    // ─── Listener ─────────────────────────────────────────────────────────────

    public interface CreateAdListener {
        void onAdCreated();
        void onCreateAdClosed();
    }

    private CreateAdListener listener;

    @Override
    public void onAttach(@NonNull Context ctx) {
        super.onAttach(ctx);
        if (ctx instanceof CreateAdListener) listener = (CreateAdListener) ctx;
    }

    // ─── Arguments / edit mode ────────────────────────────────────────────────

    private static final String ARG_EDIT_AD = "edit_ad";
    private Ad editingAd;

    public static CreateAdFragment newInstance(Ad ad) {
        CreateAdFragment f = new CreateAdFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EDIT_AD, ad.serialize());
        f.setArguments(args);
        return f;
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private static final int MAX_PHOTOS = 3;

    private final Uri[] slotUris = new Uri[MAX_PHOTOS];
    private int activeSlot = -1;
    private Uri cameraUri;

    // Views
    private TextView tvCreateAdTitle;
    private EditText etAdName, etAdArea, etAdPrice, etAdAge, etAdDescription;
    private TextView tvDescCounter;
    private ChipGroup conditionGroup, ageUnitGroup;
    private MaterialCheckBox cbPickup, cbShipping;
    private MaterialButton btnPostAd;

    private final FrameLayout[]  slotContainers = new FrameLayout[MAX_PHOTOS];
    private final ImageView[]    ivSlots        = new ImageView[MAX_PHOTOS];
    private final LinearLayout[] addIcons       = new LinearLayout[MAX_PHOTOS];

    // ─── Launchers ────────────────────────────────────────────────────────────

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCameraActual();
                else Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraUri != null && activeSlot >= 0) {
                    slotUris[activeSlot] = cameraUri;
                    loadSlotPreview(activeSlot);
                }
                cameraUri = null;
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && activeSlot >= 0) {
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}
                    slotUris[activeSlot] = uri;
                    loadSlotPreview(activeSlot);
                }
            });

    // ─── Inflate ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_ad, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        tvCreateAdTitle  = root.findViewById(R.id.tvCreateAdTitle);
        etAdName         = root.findViewById(R.id.etAdName);
        etAdArea         = root.findViewById(R.id.etAdArea);
        etAdPrice        = root.findViewById(R.id.etAdPrice);
        etAdAge          = root.findViewById(R.id.etAdAge);
        etAdDescription  = root.findViewById(R.id.etAdDescription);
        tvDescCounter    = root.findViewById(R.id.tvDescCounter);
        conditionGroup   = root.findViewById(R.id.conditionGroup);
        ageUnitGroup     = root.findViewById(R.id.ageUnitGroup);
        cbPickup         = root.findViewById(R.id.cbPickup);
        cbShipping       = root.findViewById(R.id.cbShipping);
        btnPostAd        = root.findViewById(R.id.btnPostAd);

        // Live character counter for description
        etAdDescription.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                int len = s.length();
                tvDescCounter.setText(len + " / 1000");
                tvDescCounter.setTextColor(len > 950
                        ? android.graphics.Color.parseColor("#E57373")
                        : android.graphics.Color.parseColor("#9F8FBC"));
            }
        });

        int[] containerIds = {R.id.slotContainer0, R.id.slotContainer1, R.id.slotContainer2};
        int[] ivIds        = {R.id.ivSlot0, R.id.ivSlot1, R.id.ivSlot2};
        int[] addIconIds   = {R.id.addIcon0, R.id.addIcon1, R.id.addIcon2};
        for (int i = 0; i < MAX_PHOTOS; i++) {
            final int slot = i;
            slotContainers[i] = root.findViewById(containerIds[i]);
            ivSlots[i]        = root.findViewById(ivIds[i]);
            addIcons[i]       = root.findViewById(addIconIds[i]);
            slotContainers[i].setOnClickListener(v -> openImageChooser(slot));
        }

        root.findViewById(R.id.btnCreateAdClose).setOnClickListener(v -> {
            if (listener != null) listener.onCreateAdClosed();
        });
        btnPostAd.setOnClickListener(v -> handlePost());

        // Edit mode
        if (getArguments() != null && getArguments().containsKey(ARG_EDIT_AD)) {
            editingAd = Ad.deserialize(getArguments().getString(ARG_EDIT_AD));
            if (editingAd != null) prefillForEdit(editingAd);
        }
    }

    // ─── Edit mode prefill ────────────────────────────────────────────────────

    private void prefillForEdit(Ad ad) {
        tvCreateAdTitle.setText("Edit Ad");
        btnPostAd.setText("Save Changes");

        etAdName.setText(ad.getProductName());
        etAdArea.setText(ad.getDeliveryArea());
        etAdPrice.setText(String.format(Locale.US, "%.2f", ad.getPrice()));
        if (!ad.getDescription().isEmpty()) etAdDescription.setText(ad.getDescription());
        cbPickup.setChecked(ad.isPickup());
        cbShipping.setChecked(ad.isShipping());

        // Age
        if (ad.getAgeValue() > 0) {
            etAdAge.setText(String.valueOf(ad.getAgeValue()));
            if ("months".equals(ad.getAgeUnit())) ageUnitGroup.check(R.id.btnAgeMonths);
            else if ("years".equals(ad.getAgeUnit())) ageUnitGroup.check(R.id.btnAgeYears);
        }

        // Condition chip
        int chipId;
        switch (ad.getCondition()) {
            case "Second-hand": chipId = R.id.btnCondition1; break;
            case "As new":      chipId = R.id.btnCondition2; break;
            case "Unopened":    chipId = R.id.btnCondition3; break;
            default:            chipId = R.id.btnCondition4; break;
        }
        conditionGroup.check(chipId);

        // Load existing images
        AdPreferences adPrefs = new AdPreferences(requireContext());
        for (int i = 0; i < ad.getImageCount(); i++) {
            File f = adPrefs.getImageFile(ad.getId(), i);
            if (f != null) {
                slotUris[i] = Uri.fromFile(f);
                loadSlotPreview(i);
            }
        }
    }

    // ─── Image selection ──────────────────────────────────────────────────────

    private void openImageChooser(int slot) {
        activeSlot = slot;
        boolean hasImage = slotUris[slot] != null;
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Photo")
                .setItems(hasImage
                        ? new CharSequence[]{"Camera", "Gallery", "Remove"}
                        : new CharSequence[]{"Camera", "Gallery"},
                        (dialog, which) -> {
                            if (which == 0) openCamera();
                            else if (which == 1) galleryLauncher.launch("image/*");
                            else removeSlot(activeSlot);
                        })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCameraActual();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCameraActual() {
        try {
            File tmp = File.createTempFile(
                    "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_",
                    ".jpg", requireContext().getCacheDir());
            cameraUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    tmp);
            cameraLauncher.launch(cameraUri);
        } catch (IOException e) {
            Toast.makeText(getContext(), "Camera unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadSlotPreview(int slot) {
        Uri uri = slotUris[slot];
        if (uri == null) return;
        try {
            Bitmap bmp = "file".equals(uri.getScheme())
                    ? BitmapFactory.decodeFile(uri.getPath())
                    : BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(uri));
            if (bmp != null) {
                ivSlots[slot].setImageBitmap(bmp);
                ivSlots[slot].setVisibility(View.VISIBLE);
                addIcons[slot].setVisibility(View.GONE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeSlot(int slot) {
        slotUris[slot] = null;
        ivSlots[slot].setVisibility(View.GONE);
        addIcons[slot].setVisibility(View.VISIBLE);
    }

    // ─── Post / Save ──────────────────────────────────────────────────────────

    private void handlePost() {
        String name        = etAdName.getText().toString().trim();
        String area        = etAdArea.getText().toString().trim();
        String priceStr    = etAdPrice.getText().toString().trim();
        String ageStr      = etAdAge.getText().toString().trim();
        String description = etAdDescription.getText().toString().trim();

        if (name.isEmpty())  { etAdName.setError("Please enter a product name"); return; }

        int checkedId = conditionGroup.getCheckedChipId();
        if (checkedId == View.NO_ID) {
            Toast.makeText(getContext(), "Please select a condition", Toast.LENGTH_SHORT).show();
            return;
        }

        if (area.isEmpty())  { etAdArea.setError("Please enter a delivery area"); return; }

        if (!cbPickup.isChecked() && !cbShipping.isChecked()) {
            Toast.makeText(getContext(), "Select at least one delivery option", Toast.LENGTH_SHORT).show();
            return;
        }

        if (priceStr.isEmpty()) { etAdPrice.setError("Please enter a price"); return; }

        double price;
        try { price = Double.parseDouble(priceStr); }
        catch (NumberFormatException e) { etAdPrice.setError("Invalid price"); return; }

        // Age (optional)
        int ageValue = 0;
        String ageUnit = "";
        if (!ageStr.isEmpty()) {
            try { ageValue = Integer.parseInt(ageStr); } catch (NumberFormatException ignored) {}
            int ageChipId = ageUnitGroup.getCheckedChipId();
            if (ageValue > 0 && ageChipId == View.NO_ID) {
                Toast.makeText(getContext(), "Select Months or Years for item age", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ageChipId == R.id.btnAgeMonths) ageUnit = "months";
            else if (ageChipId == R.id.btnAgeYears) ageUnit = "years";
        }

        String condition = resolveCondition(checkedId);

        List<Uri> uris = new ArrayList<>();
        for (Uri u : slotUris) { if (u != null) uris.add(u); }

        AdPreferences adPrefs = new AdPreferences(requireContext());

        if (editingAd != null) {
            adPrefs.updateAd(editingAd, name, condition, area,
                    cbPickup.isChecked(), cbShipping.isChecked(), price,
                    ageValue, ageUnit, description, uris);
            // Keep all existing chats in sync with the new product name
            new ChatPreferences(requireContext()).updateAdTitleInChats(editingAd.getId(), name);
            Toast.makeText(getContext(), "Ad updated!", Toast.LENGTH_SHORT).show();
        } else {
            UserPreferences up = new UserPreferences(requireContext());
            String[] user = up.getLoggedInUser();
            if (user == null) {
                Toast.makeText(getContext(), "You must be signed in to post an ad", Toast.LENGTH_SHORT).show();
                return;
            }
            adPrefs.saveAd(user[0], user[1], name, condition, area,
                    cbPickup.isChecked(), cbShipping.isChecked(), price,
                    ageValue, ageUnit, description, uris);
            Toast.makeText(getContext(), "Ad posted!", Toast.LENGTH_SHORT).show();
        }

        if (listener != null) listener.onAdCreated();
    }

    private String resolveCondition(int chipId) {
        if (chipId == R.id.btnCondition1) return "Second-hand";
        if (chipId == R.id.btnCondition2) return "As new";
        if (chipId == R.id.btnCondition3) return "Unopened";
        return "Other";
    }
}
