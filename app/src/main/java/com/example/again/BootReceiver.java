package com.example.again;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires on device boot so that pending notifications are re-shown even after a reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            return;
        }

        NotificationHelper.createChannel(context);

        UserPreferences userPrefs = new UserPreferences(context);
        if (!userPrefs.isLoggedIn()) return;

        String[] user = userPrefs.getLoggedInUser();
        if (user == null || user.length < 2) return;

        String email = user[1];
        NotificationHelper.checkAndNotifyUnread(context, email);
    }
}
