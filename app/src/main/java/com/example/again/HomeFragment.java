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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView        rvAllAds;
    private TextView            tvNoAdsYet;
    private SwipeRefreshLayout  swipeRefresh;

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
        rvAllAds     = root.findViewById(R.id.rvAllAds);
        tvNoAdsYet   = root.findViewById(R.id.tvNoAdsYet);
        swipeRefresh = root.findViewById(R.id.swipeRefresh);
        rvAllAds.setLayoutManager(new GridLayoutManager(getContext(), 2));

        // Style the spinner to match the app's purple theme
        swipeRefresh.setColorSchemeColors(0xFF8361C5);
        swipeRefresh.setProgressBackgroundColorSchemeColor(0xFF2D1A5C);

        swipeRefresh.setOnRefreshListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).reapplySearch();
            } else {
                refresh();
            }
        });

        refresh();
    }

    /** Show all ads with no filters. */
    public void refresh() {
        if (!isAdded() || getContext() == null) return;
        new AdPreferences(requireContext()).getAllAds(ads -> {
            if (!isAdded() || getContext() == null) return;
            showAds(ads, "No ads posted yet");
        });
    }

    /**
     * Filter and display ads matching the given query + filter options.
     * Called by MainActivity whenever the search text or filters change.
     */
    public void applySearch(String query, double maxPrice,
                            String area, boolean pickup, boolean shipping,
                            Collection<String> conditions) {
        if (!isAdded() || getContext() == null) return;

        new AdPreferences(requireContext()).getAllAds(all -> {
            if (!isAdded() || getContext() == null) return;

            List<Ad> result = new ArrayList<>();
            String q = query == null ? "" : query.toLowerCase().trim();

            for (Ad ad : all) {
                if (!q.isEmpty()
                        && !ad.getProductName().toLowerCase().contains(q)
                        && !ad.getDescription().toLowerCase().contains(q)) continue;

                if (maxPrice > 0 && ad.getPrice() > maxPrice) continue;

                if (!area.trim().isEmpty()
                        && !ad.getDeliveryArea().toLowerCase()
                               .contains(area.trim().toLowerCase())) continue;

                if (pickup && !shipping && !ad.isPickup()) continue;
                if (!pickup && shipping && !ad.isShipping()) continue;
                if (!pickup && !shipping) continue;

                if (conditions != null && !conditions.isEmpty()
                        && !conditions.contains(ad.getCondition())) continue;

                result.add(ad);
            }

            showAds(result, all.isEmpty() ? "No ads posted yet" : "No ads found");
        });
    }

    private void showAds(List<Ad> ads, String emptyMessage) {
        if (!isAdded() || getContext() == null) return;
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (ads.isEmpty()) {
            rvAllAds.setVisibility(View.GONE);
            tvNoAdsYet.setText(emptyMessage);
            tvNoAdsYet.setVisibility(View.VISIBLE);
        } else {
            tvNoAdsYet.setVisibility(View.GONE);
            rvAllAds.setVisibility(View.VISIBLE);
            rvAllAds.setAdapter(new AdAdapter(ads, null, ad ->
                    AdDetailFragment.newInstance(ad)
                            .show(getParentFragmentManager(), "ad_detail")));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).reapplySearch();
        }
    }
}
