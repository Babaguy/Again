package com.example.again;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatFragment extends Fragment {

    public interface ChatListener {
        void onChatClose();
    }

    private static final String ARG_CHAT_ID    = "chat_id";
    private static final String ARG_MY_EMAIL   = "my_email";
    private static final String ARG_OTHER_NAME = "other_name";
    private static final String ARG_AD_TITLE   = "ad_title";
    private static final String ARG_AD_ID      = "ad_id";

    /** Create a ChatFragment with all header info pre-loaded from the Chat object. */
    public static ChatFragment newInstance(String chatId, String myEmail,
                                           String otherName, String adTitle, String adId) {
        ChatFragment f = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHAT_ID,    chatId);
        args.putString(ARG_MY_EMAIL,   myEmail);
        args.putString(ARG_OTHER_NAME, otherName);
        args.putString(ARG_AD_TITLE,   adTitle);
        args.putString(ARG_AD_ID,      adId);
        f.setArguments(args);
        return f;
    }

    private String          chatId;
    private String          myEmail;
    private ChatPreferences chatPrefs;
    private ChatListener    listener;
    private RecyclerView    rvMessages;
    private MessageAdapter  adapter;
    private EditText        etMessage;

    /** Real-time Firestore listener — must be removed when the fragment is torn down. */
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ChatListener) listener = (ChatListener) context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        // Push layout above keyboard whenever the IME inset changes
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            root.setPadding(0, 0, 0, Math.max(imeBottom, navBottom));
            View topBar = root.findViewById(R.id.chatTopBar);
            if (topBar != null) topBar.setPadding(
                    topBar.getPaddingLeft(), statusTop,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            return insets;
        });

        if (getArguments() == null) { if (listener != null) listener.onChatClose(); return; }
        chatId  = getArguments().getString(ARG_CHAT_ID,    "");
        myEmail = getArguments().getString(ARG_MY_EMAIL,   "");
        String otherName = getArguments().getString(ARG_OTHER_NAME, "");
        String adTitle   = getArguments().getString(ARG_AD_TITLE,   "");
        String adId      = getArguments().getString(ARG_AD_ID,      "");

        chatPrefs = new ChatPreferences(requireContext());

        // Top bar — filled immediately from Bundle args (no network call needed)
        TextView tvName    = root.findViewById(R.id.tvChatOtherName);
        TextView tvAdTitle = root.findViewById(R.id.tvChatAdTitleBar);
        tvName.setText(otherName);
        tvAdTitle.setText(adTitle);

        root.findViewById(R.id.btnChatBack).setOnClickListener(v -> {
            hideKeyboard();
            if (listener != null) listener.onChatClose();
        });

        // Tap header → open ad detail popup
        root.findViewById(R.id.llChatHeader).setOnClickListener(v -> {
            Ad ad = AdPreferences.getCachedAd(adId);
            if (ad != null) {
                AdDetailFragment.newInstance(ad)
                        .show(getParentFragmentManager(), "ad_detail_from_chat");
            }
        });

        // Messages list
        rvMessages = root.findViewById(R.id.rvMessages);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        llm.setStackFromEnd(true);
        rvMessages.setLayoutManager(llm);

        // Input bar
        etMessage = root.findViewById(R.id.etMessage);
        ImageView btnSend       = root.findViewById(R.id.btnSend);
        ImageView btnSharePhone = root.findViewById(R.id.btnSharePhone);

        btnSend.setOnClickListener(v -> sendMessage());

        btnSharePhone.setOnClickListener(v -> {
            UserPreferences up = new UserPreferences(requireContext());
            String[] user = up.getLoggedInUser();
            String phone = (user != null && user.length > 3) ? user[3].trim() : "";
            if (!phone.isEmpty()) {
                String current = etMessage.getText().toString();
                String sep = current.isEmpty() ? "" : " ";
                etMessage.setText(current + sep + phone);
                etMessage.setSelection(etMessage.getText().length());
            } else {
                new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                        .setTitle("No phone number")
                        .setMessage("You haven't added a phone number to your account yet. "
                                + "Go to your profile to add one.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        // Start real-time listener — updates the UI automatically on every change
        messagesListener = chatPrefs.listenForMessages(chatId, this::renderMessages);

        // Watch the chat document for disabled state changes
        View inputBar      = root.findViewById(R.id.inputBar);
        android.widget.TextView tvDisabled = root.findViewById(R.id.tvChatDisabled);
        chatListener = chatPrefs.listenToChat(chatId, chat -> {
            if (!isAdded()) return;
            boolean disabled = chat != null && chat.isDisabled();
            requireActivity().runOnUiThread(() -> {
                inputBar.setVisibility(disabled ? View.GONE : View.VISIBLE);
                tvDisabled.setVisibility(disabled ? View.VISIBLE : View.GONE);
            });
        });

        // Mark as read
        chatPrefs.markChatRead(chatId, myEmail);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (chatPrefs != null) chatPrefs.markChatRead(chatId, myEmail);
        // Tell the background service not to notify for this chat while it's open
        MessageListenerService.currentOpenChatId = chatId;
    }

    @Override
    public void onPause() {
        super.onPause();
        // Allow notifications for this chat again once the user leaves it
        if (chatId.equals(MessageListenerService.currentOpenChatId)) {
            MessageListenerService.currentOpenChatId = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
    }

    // ── Render messages ───────────────────────────────────────────────────────

    /** Called by the real-time listener every time the message list changes. */
    private void renderMessages(List<Message> raw) {
        if (!isAdded() || getContext() == null) return;

        // Insert synthetic date-separator entries
        List<Message> withDates = new ArrayList<>();
        String lastDay = null;
        for (Message m : raw) {
            String day = dayKey(m.getTimestamp());
            if (!day.equals(lastDay)) {
                Message sep = new Message(
                        UUID.randomUUID().toString(), chatId, "",
                        formatDayLabel(m.getTimestamp()),
                        m.getTimestamp(), Message.TYPE_DATE);
                withDates.add(sep);
                lastDay = day;
            }
            withDates.add(m);
        }

        boolean wasAtBottom = isAtBottom();

        if (adapter == null) {
            adapter = new MessageAdapter(withDates, myEmail, this::showMessageOptions);
            rvMessages.setAdapter(adapter);
        } else {
            adapter.updateMessages(withDates);
        }

        // Scroll to bottom when a new message arrives or on first load
        if (wasAtBottom || adapter.getItemCount() <= withDates.size()) {
            rvMessages.scrollToPosition(withDates.size() - 1);
        }
    }

    private boolean isAtBottom() {
        if (rvMessages == null) return true;
        LinearLayoutManager llm = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (llm == null) return true;
        int last = llm.findLastVisibleItemPosition();
        int count = llm.getItemCount();
        return last >= count - 2; // within 2 items of the bottom
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    private String dayKey(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
    }

    private String formatDayLabel(long timestamp) {
        Calendar msgCal   = Calendar.getInstance();
        Calendar today     = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (isSameDay(msgCal, today))     return "Today";
        if (isSameDay(msgCal, yesterday)) return "Yesterday";
        if (msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR))
            return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date(timestamp));
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR)       == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        etMessage.setText("");
        // Listener fires automatically when Firestore confirms the write
        chatPrefs.sendMessage(chatId, myEmail, text);
    }

    // ── Message long-press options ────────────────────────────────────────────

    private void showMessageOptions(Message message) {
        if (!message.getSenderEmail().equalsIgnoreCase(myEmail)) return;

        boolean canEdit = message.canEdit();
        CharSequence[] items = canEdit
                ? new CharSequence[]{"Edit message", "Delete message"}
                : new CharSequence[]{"Delete message"};

        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setItems(items, (dialog, which) -> {
                    if (canEdit && which == 0) showEditDialog(message);
                    else confirmDelete(message);
                })
                .show();
    }

    private void showEditDialog(Message message) {
        EditText input = new EditText(requireContext());
        input.setText(message.getText());
        input.setTextColor(0xFFE6DCF6);
        input.setBackground(null);
        input.setSelection(message.getText().length());
        int pad = dpToPx(20);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Edit message")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String newText = input.getText().toString().trim();
                    if (newText.isEmpty()) {
                        Toast.makeText(getContext(), "Message cannot be empty",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Listener fires automatically on success; onFail shows a toast
                    chatPrefs.editMessage(chatId, message.getMsgId(), myEmail, newText, () -> {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(),
                                        "Edit window has passed", Toast.LENGTH_SHORT).show());
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Message message) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Delete message?")
                .setMessage("Everyone in this chat will see that the message was deleted.")
                .setPositiveButton("Delete", (d, w) ->
                        chatPrefs.deleteMessage(chatId, message.getMsgId(), myEmail))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void hideKeyboard() {
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            InputMethodManager imm = (InputMethodManager)
                    requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
