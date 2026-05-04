package com.example.again;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class LeftFragment extends Fragment {

    private RecyclerView rvMyAds;
    private LinearLayout llSignInRequired;
    private LinearLayout llNoAds;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_left, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        rvMyAds          = root.findViewById(R.id.rvMyAds);
        llSignInRequired = root.findViewById(R.id.llSignInRequired);
        llNoAds          = root.findViewById(R.id.llNoAds);

        rvMyAds.setLayoutManager(new GridLayoutManager(getContext(), 2));
        refresh();
    }

    /** Called by MainActivity after a new/edited ad is saved or login state changes. */
    public void refresh() {
        if (!isAdded() || getContext() == null) return;

        UserPreferences up = new UserPreferences(requireContext());
        if (!up.isLoggedIn()) {
            rvMyAds.setVisibility(View.GONE);
            llNoAds.setVisibility(View.GONE);
            llSignInRequired.setVisibility(View.VISIBLE);
            return;
        }

        String[] user = up.getLoggedInUser();
        String email = user != null ? user[1] : "";

        AdPreferences adPrefs = new AdPreferences(requireContext());
        List<Ad> myAds = adPrefs.getAdsByOwner(email);

        if (myAds.isEmpty()) {
            rvMyAds.setVisibility(View.GONE);
            llSignInRequired.setVisibility(View.GONE);
            llNoAds.setVisibility(View.VISIBLE);
        } else {
            llSignInRequired.setVisibility(View.GONE);
            llNoAds.setVisibility(View.GONE);
            rvMyAds.setVisibility(View.VISIBLE);
            rvMyAds.setAdapter(new AdAdapter(
                    myAds,
                    adPrefs,
                    // Tap → detail popup
                    ad -> AdDetailFragment.newInstance(ad)
                            .show(getParentFragmentManager(), "ad_detail"),
                    // Long-press → edit / delete menu
                    ad -> showEditDeleteDialog(ad)
            ));
        }
    }

    private void showEditDeleteDialog(Ad ad) {
        CharSequence[] items = ad.isSold()
                // Sold: no editing allowed — only undo or delete
                ? new CharSequence[]{"↩  Mark as Available", "🗑  Delete"}
                // Active: full options
                : new CharSequence[]{"✏  Edit", "✓  Mark as Sold", "🗑  Delete"};

        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle(ad.getProductName())
                .setItems(items, (dialog, which) -> {
                    if (ad.isSold()) {
                        if (which == 0) confirmMarkAvailable(ad);
                        else if (which == 1) confirmDelete(ad);
                    } else {
                        if (which == 0 && getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).openEditAd(ad);
                        } else if (which == 1) {
                            confirmMarkSold(ad);
                        } else if (which == 2) {
                            confirmDelete(ad);
                        }
                    }
                })
                .show();
    }

    private void confirmMarkAvailable(Ad ad) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Mark as available?")
                .setMessage("\"" + ad.getProductName() + "\" will be listed again and all interested buyers will be notified.")
                .setPositiveButton("Mark as Available", (d, w) -> {
                    new AdPreferences(requireContext()).unmarkSold(ad.getId());

                    UserPreferences up = new UserPreferences(requireContext());
                    String[] user = up.getLoggedInUser();
                    if (user != null) {
                        new ChatPreferences(requireContext())
                                .sendUnsoldNotification(ad.getId(), user[1], ad.getProductName());
                    }

                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmMarkSold(Ad ad) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Mark as sold?")
                .setMessage("\"" + ad.getProductName() + "\" will be marked as sold and all buyers will be notified.")
                .setPositiveButton("Mark as Sold", (d, w) -> {
                    new AdPreferences(requireContext()).markSold(ad.getId());

                    // Notify all open chats about this ad
                    UserPreferences up = new UserPreferences(requireContext());
                    String[] user = up.getLoggedInUser();
                    if (user != null) {
                        new ChatPreferences(requireContext())
                                .sendSoldNotification(ad.getId(), user[1], ad.getProductName());
                    }

                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Ad ad) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Remove ad?")
                .setMessage("\"" + ad.getProductName() + "\" will be permanently removed.")
                .setPositiveButton("Remove", (d, w) -> {
                    new AdPreferences(requireContext()).deleteAd(ad.getId());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }
}
