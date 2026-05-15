package com.example.again;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMessageLongClickListener {
        /** Called only for own, non-product-card messages. */
        void onMessageLongClick(Message message);
    }

    private static final int VT_SENT         = 0;
    private static final int VT_RECEIVED     = 1;
    private static final int VT_PRODUCT_CARD = 2;
    private static final int VT_SYSTEM       = 3;
    private static final int VT_DATE         = 4;

    private List<Message>                    messages;
    private final String                    myEmail;
    private final OnMessageLongClickListener longClickListener;

    public MessageAdapter(List<Message> messages, String myEmail,
                          OnMessageLongClickListener longClick) {
        this.messages          = messages;
        this.myEmail           = myEmail;
        this.longClickListener = longClick;
    }

    /** Replace the message list (called by the real-time Firestore listener). */
    public void updateMessages(List<Message> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Message m = messages.get(position);
        if (Message.TYPE_DATE.equals(m.getType()))         return VT_DATE;
        if (Message.TYPE_SYSTEM.equals(m.getType()))       return VT_SYSTEM;
        if (Message.TYPE_PRODUCT_CARD.equals(m.getType())) return VT_PRODUCT_CARD;
        return m.getSenderEmail().equalsIgnoreCase(myEmail) ? VT_SENT : VT_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VT_SENT:
                return new SentVH(inf.inflate(R.layout.item_message_sent, parent, false));
            case VT_RECEIVED:
                return new ReceivedVH(inf.inflate(R.layout.item_message_received, parent, false));
            case VT_SYSTEM:
                return new SystemVH(inf.inflate(R.layout.item_message_system, parent, false));
            case VT_DATE:
                return new DateSeparatorVH(inf.inflate(R.layout.item_date_separator, parent, false));
            default:
                return new ProductCardVH(inf.inflate(R.layout.item_message_product_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message m = messages.get(position);

        if (holder instanceof DateSeparatorVH) {
            ((DateSeparatorVH) holder).tvDate.setText(m.getText());
            return;
        }

        if (holder instanceof SystemVH) {
            ((SystemVH) holder).tvText.setText(m.getText());
            return;
        }

        if (holder instanceof ProductCardVH) {
            ProductCardVH h = (ProductCardVH) holder;
            h.tvProductTitle.setText(m.getProductTitle());
            h.tvProductPrice.setText(m.getProductPrice());
            return;
        }

        String timeStr = formatTime(m.getTimestamp());

        if (holder instanceof SentVH) {
            SentVH h = (SentVH) holder;
            if (m.isDeleted()) {
                h.tvText.setText("Message deleted");
                h.tvText.setAlpha(0.5f);
                h.tvText.setTextAppearance(android.R.style.TextAppearance_Small);
            } else {
                h.tvText.setText(m.getText());
                h.tvText.setAlpha(1f);
            }
            String metaText = timeStr;
            if (m.getEditedTimestamp() > 0 && !m.isDeleted()) metaText += "  edited";
            h.tvMeta.setText(metaText);

            // Long-press to edit/delete own non-deleted non-card messages
            if (!m.isDeleted()) {
                h.itemView.setOnLongClickListener(v -> {
                    if (longClickListener != null) longClickListener.onMessageLongClick(m);
                    return true;
                });
            } else {
                h.itemView.setOnLongClickListener(null);
            }

        } else if (holder instanceof ReceivedVH) {
            ReceivedVH h = (ReceivedVH) holder;
            if (m.isDeleted()) {
                h.tvText.setText("Message deleted");
                h.tvText.setAlpha(0.5f);
            } else {
                h.tvText.setText(m.getText());
                h.tvText.setAlpha(1f);
            }
            h.tvTime.setText(timeStr);
        }
    }

    @Override
    public int getItemCount() { return messages.size(); }

    private String formatTime(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class SentVH extends RecyclerView.ViewHolder {
        TextView tvText, tvMeta;
        SentVH(View v) {
            super(v);
            tvText = v.findViewById(R.id.tvSentText);
            tvMeta = v.findViewById(R.id.tvSentMeta);
        }
    }

    static class ReceivedVH extends RecyclerView.ViewHolder {
        TextView tvText, tvTime;
        ReceivedVH(View v) {
            super(v);
            tvText = v.findViewById(R.id.tvReceivedText);
            tvTime = v.findViewById(R.id.tvReceivedTime);
        }
    }

    static class DateSeparatorVH extends RecyclerView.ViewHolder {
        TextView tvDate;
        DateSeparatorVH(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDateSeparator);
        }
    }

    static class SystemVH extends RecyclerView.ViewHolder {
        TextView tvText;
        SystemVH(View v) {
            super(v);
            tvText = v.findViewById(R.id.tvSystemMessage);
        }
    }

    static class ProductCardVH extends RecyclerView.ViewHolder {
        TextView tvProductTitle, tvProductPrice;
        ProductCardVH(View v) {
            super(v);
            tvProductTitle = v.findViewById(R.id.tvCardTitle);
            tvProductPrice = v.findViewById(R.id.tvCardPrice);
        }
    }
}
