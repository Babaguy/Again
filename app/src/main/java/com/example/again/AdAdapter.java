package com.example.again;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class AdAdapter extends RecyclerView.Adapter<AdAdapter.AdViewHolder> {

    public interface OnAdClickListener {
        void onAdClick(Ad ad);
    }

    public interface OnAdLongClickListener {
        void onAdLongClick(Ad ad);
    }

    private final List<Ad> ads;
    private final AdPreferences adPrefs;
    private final OnAdClickListener clickListener;
    @Nullable private final OnAdLongClickListener longClickListener;

    public AdAdapter(List<Ad> ads, AdPreferences adPrefs,
                     OnAdClickListener clickListener,
                     @Nullable OnAdLongClickListener longClickListener) {
        this.ads = ads;
        this.adPrefs = adPrefs;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    /** Convenience constructor without long-press (used in HomeFragment). */
    public AdAdapter(List<Ad> ads, AdPreferences adPrefs, OnAdClickListener clickListener) {
        this(ads, adPrefs, clickListener, null);
    }

    @NonNull
    @Override
    public AdViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.ad_item, parent, false);
        return new AdViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull AdViewHolder holder, int position) {
        Ad ad = ads.get(position);

        holder.tvName.setText(ad.getProductName());
        holder.tvPrice.setText(String.format(Locale.US, "₪%.2f", ad.getPrice()));
        holder.tvCondition.setText(ad.getCondition());

        // Reset image placeholder first
        holder.ivImage.setImageResource(R.drawable.ic_add_photo);
        holder.ivImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        if (ad.getImageCount() > 0) {
            Bitmap bmp = adPrefs.loadImage(ad.getId(), 0);
            if (bmp != null) {
                holder.ivImage.setImageBitmap(bmp);
                holder.ivImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        }

        // SOLD overlay
        if (holder.soldOverlay != null) {
            holder.soldOverlay.setVisibility(ad.isSold() ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onAdClick(ad);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onAdLongClick(ad);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return ads.size(); }

    static class AdViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivImage;
        final TextView  tvName, tvPrice, tvCondition;
        final View      soldOverlay;

        AdViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage     = itemView.findViewById(R.id.ivAdImage);
            tvName      = itemView.findViewById(R.id.tvAdName);
            tvPrice     = itemView.findViewById(R.id.tvAdPrice);
            tvCondition = itemView.findViewById(R.id.tvAdCondition);
            soldOverlay = itemView.findViewById(R.id.soldOverlay);
        }
    }
}
