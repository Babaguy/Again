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

    private static final String ARG_CHAT_ID  = "chat_id";
    private static final String ARG_MY_EMAIL = "my_email";

    public static ChatFragment newInstance(String chatId, String myEmail) {
        ChatFragment f = new ChatFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHAT_ID,  chatId);
        args.putString(ARG_MY_EMAIL, myEmail);
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

        // Push layout above the keyboard whenever the IME inset changes
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            int statusTop = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            root.setPadding(0, 0, 0, Math.max(imeBottom, navBottom));
            // Apply real status-bar height as top padding on the chat top bar
            View topBar = root.findViewById(R.id.chatTopBar);
            if (topBar != null) topBar.setPadding(
                    topBar.getPaddingLeft(), statusTop,
                    topBar.getPaddingRight(), topBar.getPaddingBottom());
            return insets;
        });

        if (getArguments() == null) { if (listener != null) listener.onChatClose(); return; }
        chatId  = getArguments().getString(ARG_CHAT_ID, "");
        myEmail = getArguments().getString(ARG_MY_EMAIL, "");

        chatPrefs = new ChatPreferences(requireContext());

        Chat chat = chatPrefs.getChatById(chatId);
        if (chat == null) { if (listener != null) listener.onChatClose(); return; }

        // Top bar
        TextView tvName    = root.findViewById(R.id.tvChatOtherName);
        TextView tvAdTitle = root.findViewById(R.id.tvChatAdTitleBar);
        tvName.setText(chat.getOtherUserName(myEmail));
        tvAdTitle.setText(chat.getAdTitle());

        root.findViewById(R.id.btnChatBack).setOnClickListener(v -> {
            hideKeyboard();
            if (listener != null) listener.onChatClose();
        });

        // Tap the header → open ad detail popup
        root.findViewById(R.id.llChatHeader).setOnClickListener(v -> {
            AdPreferences adPrefs = new AdPreferences(requireContext());
            Ad ad = adPrefs.getAdById(chat.getAdId());
            if (ad != null) {
                AdDetailFragment.newInstance(ad)
                        .show(getParentFragmentManager(), "ad_detail_from_chat");
            }
        });

        // Messages RecyclerView
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
                // Append phone number to whatever is already typed
                String current = etMessage.getText().toString();
                String separator = current.isEmpty() ? "" : " ";
                etMessage.setText(current + separator + phone);
                etMessage.setSelection(etMessage.getText().length());
            } else {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                        requireContext(), R.style.PurpleAlertDialog)
                        .setTitle("No phone number")
                        .setMessage("You haven't added a phone number to your account yet. Go to your profile to add one.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });

        loadMessages();

        // Mark as read
        chatPrefs.markChatRead(chatId, myEmail);
    }

    @Override
    public void onResume() {
        super.onResume();
        chatPrefs.markChatRead(chatId, myEmail);
    }

    private void loadMessages() {
        List<Message> raw = chatPrefs.getMessages(chatId);
        List<Message> withDates = new ArrayList<>();

        String lastDay = null;
        for (Message m : raw) {
            String day = dayKey(m.getTimestamp());
            if (!day.equals(lastDay)) {
                // Insert a synthetic date-separator message
                Message sep = new Message(
                        UUID.randomUUID().toString(), chatId, "",
                        formatDayLabel(m.getTimestamp()),
                        m.getTimestamp(), Message.TYPE_DATE);
                withDates.add(sep);
                lastDay = day;
            }
            withDates.add(m);
        }

        adapter = new MessageAdapter(withDates, myEmail, this::showMessageOptions);
        rvMessages.setAdapter(adapter);
        if (!withDates.isEmpty()) rvMessages.scrollToPosition(withDates.size() - 1);
    }

    /** Returns a string like "2024-05-04" used only for day-boundary comparison. */
    private String dayKey(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
    }

    /** Returns "Today", "Yesterday", or "Mon, 4 May 2024". */
    private String formatDayLabel(long timestamp) {
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTimeInMillis(timestamp);

        Calendar today = Calendar.getInstance();
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);

        if (isSameDay(msgCal, today))     return "Today";
        if (isSameDay(msgCal, yesterday)) return "Yesterday";

        // Same year: omit the year
        if (msgCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date(timestamp));
        }
        return new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR)       == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        etMessage.setText("");

        chatPrefs.sendMessage(chatId, myEmail, text);
        loadMessages();
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
                    if (canEdit) {
                        if (which == 0) showEditDialog(message);
                        else confirmDelete(message);
                    } else {
                        confirmDelete(message);
                    }
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
                        Toast.makeText(getContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean ok = chatPrefs.editMessage(chatId, message.getMsgId(), myEmail, newText);
                    if (!ok) Toast.makeText(getContext(), "Edit window has passed", Toast.LENGTH_SHORT).show();
                    loadMessages();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Message message) {
        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle("Delete message?")
                .setMessage("Everyone in this chat will see that the message was deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    chatPrefs.deleteMessage(chatId, message.getMsgId(), myEmail);
                    loadMessages();
                })
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
