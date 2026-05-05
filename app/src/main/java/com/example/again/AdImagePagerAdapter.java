package com.example.again;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AdImagePagerAdapter extends RecyclerView.Adapter<AdImagePagerAdapter.ImageVH> {

    public interface OnImageClickListener {
        void onImageClick(Bitmap bitmap);
    }

    private final List<Bitmap> bitmaps;
    private final OnImageClickListener clickListener;

    public AdImagePagerAdapter(List<Bitmap> bitmaps, OnImageClickListener clickListener) {
        this.bitmaps = bitmaps;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ImageVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImageView iv = new ImageView(parent.getContext());
        iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return new ImageVH(iv);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageVH holder, int position) {
        Bitmap bmp = bitmaps.get(position);
        holder.imageView.setImageBitmap(bmp);
        holder.imageView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onImageClick(bmp);
        });
    }

    @Override
    public int getItemCount() { return bitmaps.size(); }

    static class ImageVH extends RecyclerView.ViewHolder {
        final ImageView imageView;
        ImageVH(ImageView iv) {
            super(iv);
            imageView = iv;
        }
    }
}
