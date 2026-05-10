package com.example.again;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;

public class HomeFragment extends Fragment {

    private RecyclerView rvAllAds;
    private TextView tvNoAdsYet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_center, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        rvAllAds   = root.findViewById(R.id.rvAllAds);
        tvNoAdsYet = root.findViewById(R.id.tvNoAdsYet);
        rvAllAds.setLayoutManager(new GridLayoutManager(getContext(), 2));
        refresh();
    }

    /** Show all ads with no filters (default state). */
    public void refresh() {
        if (!isAdded() || getContext() == null) return;
        AdPreferences adPrefs = new AdPreferences(requireContext());
        showAds(adPrefs.getAllAds(), adPrefs);
    }

    /**
     * Filter and display ads matching the given query + filter options.
     * Called by MainActivity whenever the search text or filters change.
     */
    public void applySearch(String query, double maxPrice,
                            String area, boolean pickup, boolean shipping,
                            Collection<String> conditions) {
        if (!isAdded() || getContext() == null) return;

        AdPreferences adPrefs = new AdPreferences(requireContext());
        List<Ad> all = adPrefs.getAllAds();
        List<Ad> result = new ArrayList<>();

        String q = query == null ? "" : query.toLowerCase().trim();

        for (Ad ad : all) {
            // Text search — name or description
            if (!q.isEmpty()
                    && !ad.getProductName().toLowerCase().contains(q)
                    && !ad.getDescription().toLowerCase().contains(q)) continue;

            // Max price
            if (maxPrice > 0 && ad.getPrice() > maxPrice) continue;

            // Area (partial match)
            if (!area.trim().isEmpty()
                    && !ad.getDeliveryArea().toLowerCase()
                           .contains(area.trim().toLowerCase())) continue;

            // Delivery options
            if (pickup && !shipping && !ad.isPickup()) continue;
            if (!pickup && shipping && !ad.isShipping()) continue;
            if (!pickup && !shipping) continue; // nothing checked → skip all

            // Condition — empty list means "any"
            if (conditions != null && !conditions.isEmpty()
                    && !conditions.contains(ad.getCondition())) continue;

            result.add(ad);
        }

        // If the full list is also empty, keep the original "no ads yet" message.
        // Only show "no ads found" when there ARE ads but none matched the search.
        showAds(result, adPrefs, all.isEmpty() ? "No ads posted yet" : "No ads found");
    }

    private void showAds(List<Ad> ads, AdPreferences adPrefs) {
        showAds(ads, adPrefs, "No ads posted yet");
    }

    private void showAds(List<Ad> ads, AdPreferences adPrefs, String emptyMessage) {
        if (!isAdded() || getContext() == null) return;
        if (ads.isEmpty()) {
            rvAllAds.setVisibility(View.GONE);
            tvNoAdsYet.setText(emptyMessage);
            tvNoAdsYet.setVisibility(View.VISIBLE);
        } else {
            tvNoAdsYet.setVisibility(View.GONE);
            rvAllAds.setVisibility(View.VISIBLE);
            rvAllAds.setAdapter(new AdAdapter(ads, adPrefs, ad ->
                    AdDetailFragment.newInstance(ad)
                            .show(getParentFragmentManager(), "ad_detail")));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-apply whatever the activity currently has as active filters
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).reapplySearch();
        }
    }
}
