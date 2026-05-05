package com.example.again;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatVH> {

    public interface OnChatClickListener    { void onChatClick(Chat chat); }
    public interface OnChatLongClickListener { void onChatLongClick(Chat chat); }

    private final List<Chat>          chats;
    private final String              myEmail;
    private final ChatPreferences     chatPrefs;
    private final OnChatClickListener clickListener;
    private final OnChatLongClickListener longClickListener;

    public ChatListAdapter(List<Chat> chats, String myEmail, ChatPreferences chatPrefs,
                           OnChatClickListener click, OnChatLongClickListener longClick) {
        this.chats             = chats;
        this.myEmail           = myEmail;
        this.chatPrefs         = chatPrefs;
        this.clickListener     = click;
        this.longClickListener = longClick;
    }

    @NonNull
    @Override
    public ChatVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatVH h, int position) {
        Chat chat = chats.get(position);
        Context ctx = h.itemView.getContext();

        String otherName  = chat.getOtherUserName(myEmail);
        String initial    = otherName.isEmpty() ? "?" : String.valueOf(otherName.charAt(0)).toUpperCase();

        // Avatar
        h.tvInitial.setText(initial);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#3D1A7A"));
        h.tvInitial.setBackground(circle);

        // Name and ad title
        h.tvName.setText(otherName);
        h.tvAdTitle.setText(chat.getAdTitle());

        // Last message preview
        String lastMsg = chat.getLastMessageText();
        if (lastMsg == null || lastMsg.isEmpty()) lastMsg = "…";
        boolean muted = chatPrefs.isMuted(chat.getChatId(), myEmail);
        h.tvLastMessage.setText(muted ? "🔕 " + lastMsg : lastMsg);

        // Timestamp
        h.tvTimestamp.setText(formatTimestamp(chat.getLastMessageTimestamp()));

        // Unread badge
        int unread = chatPrefs.getUnreadCount(chat.getChatId(), myEmail);
        if (unread > 0) {
            h.tvUnread.setVisibility(View.VISIBLE);
            h.tvUnread.setText(unread > 99 ? "99+" : String.valueOf(unread));
        } else {
            h.tvUnread.setVisibility(View.GONE);
        }

        // Mute icon
        h.ivMute.setVisibility(muted ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onChatClick(chat); });
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onChatLongClick(chat);
            return true;
        });
    }

    @Override
    public int getItemCount() { return chats.size(); }

    private String formatTimestamp(long ts) {
        if (ts == 0) return "";
        long now = System.currentTimeMillis();
        long diff = now - ts;
        if (diff < 60_000) return "now";
        if (diff < 3_600_000) return (diff / 60_000) + "m";
        if (diff < 86_400_000) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        return new SimpleDateFormat("d MMM", Locale.getDefault()).format(new Date(ts));
    }

    static class ChatVH extends RecyclerView.ViewHolder {
        TextView  tvInitial, tvName, tvAdTitle, tvLastMessage, tvTimestamp, tvUnread;
        ImageView ivMute;

        ChatVH(View v) {
            super(v);
            tvInitial     = v.findViewById(R.id.tvChatInitial);
            tvName        = v.findViewById(R.id.tvChatName);
            tvAdTitle     = v.findViewById(R.id.tvChatAdTitle);
            tvLastMessage = v.findViewById(R.id.tvChatLastMessage);
            tvTimestamp   = v.findViewById(R.id.tvChatTimestamp);
            tvUnread      = v.findViewById(R.id.tvChatUnread);
            ivMute        = v.findViewById(R.id.ivChatMute);
        }
    }
}
