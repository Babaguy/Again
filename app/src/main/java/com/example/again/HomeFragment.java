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

import java.util.List;

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

    /** Called by MainActivity after a new ad is posted. */
    public void refresh() {
        if (!isAdded() || getContext() == null) return;

        AdPreferences adPrefs = new AdPreferences(requireContext());
        List<Ad> allAds = adPrefs.getAllAds();

        if (allAds.isEmpty()) {
            rvAllAds.setVisibility(View.GONE);
            tvNoAdsYet.setVisibility(View.VISIBLE);
        } else {
            tvNoAdsYet.setVisibility(View.GONE);
            rvAllAds.setVisibility(View.VISIBLE);
            rvAllAds.setAdapter(new AdAdapter(allAds, adPrefs, ad ->
                    AdDetailFragment.newInstance(ad)
                            .show(getParentFragmentManager(), "ad_detail")));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }
}
