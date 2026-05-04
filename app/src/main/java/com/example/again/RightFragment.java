package com.example.again;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class RightFragment extends Fragment {

    private RecyclerView rvChats;
    private RecyclerView rvArchivedChats;
    private View         llNoChats;
    private View         llSignInChats;
    private TextView     tvArchivedToggle;
    private TextView     tabBuying;
    private TextView     tabSelling;

    private boolean archivedExpanded = false;
    private boolean showingSelling   = false;  // false = Buying, true = Selling

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        rvChats          = root.findViewById(R.id.rvChats);
        rvArchivedChats  = root.findViewById(R.id.rvArchivedChats);
        llNoChats        = root.findViewById(R.id.llNoChats);
        llSignInChats    = root.findViewById(R.id.llSignInChats);
        tvArchivedToggle = root.findViewById(R.id.tvArchivedToggle);
        tabBuying        = root.findViewById(R.id.tabBuying);
        tabSelling       = root.findViewById(R.id.tabSelling);

        rvChats.setLayoutManager(new LinearLayoutManager(getContext()));
        rvArchivedChats.setLayoutManager(new LinearLayoutManager(getContext()));

        tabBuying.setOnClickListener(v -> {
            if (showingSelling) {
                showingSelling = false;
                archivedExpanded = false;
                updateTabAppearance();
                refresh();
            }
        });

        tabSelling.setOnClickListener(v -> {
            if (!showingSelling) {
                showingSelling = true;
                archivedExpanded = false;
                updateTabAppearance();
                refresh();
            }
        });

        tvArchivedToggle.setOnClickListener(v -> {
            archivedExpanded = !archivedExpanded;
            rvArchivedChats.setVisibility(archivedExpanded ? View.VISIBLE : View.GONE);
            refresh();
        });

        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        // Check and fire notifications for unread messages
        if (getContext() != null) {
            UserPreferences up = new UserPreferences(requireContext());
            if (up.isLoggedIn()) {
                String[] user = up.getLoggedInUser();
                if (user != null)
                    NotificationHelper.checkAndNotifyUnread(requireContext(), user[1]);
            }
        }
    }

    public void refresh() {
        if (!isAdded() || getContext() == null) return;

        UserPreferences up = new UserPreferences(requireContext());
        if (!up.isLoggedIn()) {
            rvChats.setVisibility(View.GONE);
            llNoChats.setVisibility(View.GONE);
            tvArchivedToggle.setVisibility(View.GONE);
            rvArchivedChats.setVisibility(View.GONE);
            llSignInChats.setVisibility(View.VISIBLE);
            return;
        }

        llSignInChats.setVisibility(View.GONE);

        String[] user  = up.getLoggedInUser();
        String myEmail = user != null ? user[1] : "";

        ChatPreferences chatPrefs = new ChatPreferences(requireContext());

        // Filter by tab: Buying = I'm the buyer, Selling = I'm the seller
        List<Chat> active   = filter(chatPrefs.getActiveChatsForUser(myEmail),   myEmail);
        List<Chat> archived = filter(chatPrefs.getArchivedChatsForUser(myEmail), myEmail);

        if (active.isEmpty() && archived.isEmpty()) {
            rvChats.setVisibility(View.GONE);
            tvArchivedToggle.setVisibility(View.GONE);
            rvArchivedChats.setVisibility(View.GONE);
            llNoChats.setVisibility(View.VISIBLE);
            return;
        }

        llNoChats.setVisibility(View.GONE);
        rvChats.setVisibility(View.VISIBLE);

        rvChats.setAdapter(new ChatListAdapter(active, myEmail, chatPrefs,
                chat -> openChat(chat, myEmail),
                chat -> showChatOptions(chat, myEmail, chatPrefs)));

        // Archived section
        if (!archived.isEmpty()) {
            int count = archived.size();
            tvArchivedToggle.setVisibility(View.VISIBLE);
            tvArchivedToggle.setText(archivedExpanded
                    ? "▲  Hide archived (" + count + ")"
                    : "▼  Archived (" + count + ")");
            if (archivedExpanded) {
                rvArchivedChats.setVisibility(View.VISIBLE);
                rvArchivedChats.setAdapter(new ChatListAdapter(archived, myEmail, chatPrefs,
                        chat -> openChat(chat, myEmail),
                        chat -> showChatOptions(chat, myEmail, chatPrefs)));
            } else {
                rvArchivedChats.setVisibility(View.GONE);
            }
        } else {
            tvArchivedToggle.setVisibility(View.GONE);
            rvArchivedChats.setVisibility(View.GONE);
        }
    }

    /** Keep only chats where the current user is buyer (Buying tab) or seller (Selling tab). */
    private List<Chat> filter(List<Chat> chats, String myEmail) {
        List<Chat> result = new ArrayList<>();
        for (Chat c : chats) {
            boolean isSeller = c.getSellerEmail().equalsIgnoreCase(myEmail);
            if (showingSelling == isSeller) result.add(c);
        }
        return result;
    }

    private void updateTabAppearance() {
        if (tabBuying == null || tabSelling == null) return;
        if (showingSelling) {
            tabBuying.setBackground(
                    androidx.core.content.ContextCompat.getDrawable(requireContext(),
                            R.drawable.filter_tab_inactive_bg));
            tabBuying.setTextColor(android.graphics.Color.parseColor("#9F8FBC"));
            tabSelling.setBackground(
                    androidx.core.content.ContextCompat.getDrawable(requireContext(),
                            R.drawable.filter_tab_active_bg));
            tabSelling.setTextColor(android.graphics.Color.WHITE);
        } else {
            tabBuying.setBackground(
                    androidx.core.content.ContextCompat.getDrawable(requireContext(),
                            R.drawable.filter_tab_active_bg));
            tabBuying.setTextColor(android.graphics.Color.WHITE);
            tabSelling.setBackground(
                    androidx.core.content.ContextCompat.getDrawable(requireContext(),
                            R.drawable.filter_tab_inactive_bg));
            tabSelling.setTextColor(android.graphics.Color.parseColor("#9F8FBC"));
        }
    }

    private void openChat(Chat chat, String myEmail) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openChat(chat, myEmail);
        }
    }

    private void showChatOptions(Chat chat, String myEmail, ChatPreferences chatPrefs) {
        boolean muted    = chatPrefs.isMuted(chat.getChatId(), myEmail);
        boolean archived = chatPrefs.isArchived(chat.getChatId(), myEmail);

        new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                .setTitle(chat.getOtherUserName(myEmail))
                .setItems(new CharSequence[]{
                        muted    ? "Unmute notifications" : "Mute notifications",
                        archived ? "Unarchive"            : "Archive",
                        "Delete chat"
                }, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            chatPrefs.muteChat(chat.getChatId(), myEmail, !muted);
                            refresh();
                            break;
                        case 1:
                            chatPrefs.archiveChat(chat.getChatId(), myEmail, !archived);
                            archivedExpanded = !archived;
                            refresh();
                            break;
                        case 2:
                            new MaterialAlertDialogBuilder(requireContext(), R.style.PurpleAlertDialog)
                                    .setTitle("Delete conversation?")
                                    .setMessage("This removes the chat from your list. The other person will be notified.")
                                    .setPositiveButton("Delete", (d, w) -> {
                                        UserPreferences upDel = new UserPreferences(requireContext());
                                        String[] userDel = upDel.getLoggedInUser();
                                        String displayName = (userDel != null) ? userDel[0] : myEmail;
                                        chatPrefs.deleteChat(chat.getChatId(), myEmail, displayName);
                                        refresh();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            break;
                    }
                })
                .show();
    }
}
