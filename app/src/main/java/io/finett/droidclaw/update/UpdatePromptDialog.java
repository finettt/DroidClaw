package io.finett.droidclaw.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.finett.droidclaw.R;

/**
 * The one-screen "new version available" dialog, shared by the Info-screen
 * manual check and the startup check. Opening the release lets the browser
 * download the APK; nothing is downloaded or installed inside the app.
 */
public final class UpdatePromptDialog {

    /** Soft cap on the release notes shown in the message body. */
    public static final int MAX_MESSAGE_CHARS = 1500;

    private UpdatePromptDialog() {
    }

    /**
     * Shows the update dialog for {@code info}. No-op if {@code info} is null.
     *
     * @param context a host context (activity)
     * @param info    the release to announce
     */
    public static void show(@Nullable Context context, @Nullable UpdateInfo info) {
        if (context == null || info == null || TextUtils.isEmpty(info.getTag())) {
            return;
        }
        String tag = info.getTag();
        String title = context.getString(R.string.update_dialog_title,
                context.getString(R.string.app_name) + " " + tag);
        String message = truncate(info.getBody());

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.update_dialog_open, (dialog, which) -> openRelease(context, info))
                .setNegativeButton(R.string.update_dialog_later, null)
                .show();
    }

    private static void openRelease(Context context, UpdateInfo info) {
        String url = info.getHtmlUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        context.startActivity(intent);
    }

    private static String truncate(@Nullable String body) {
        if (body == null) {
            return "";
        }
        // Strip HTML tags and collapse whitespace so release notes read as plain text.
        String text = body.replaceAll("(?s)<[^>]*>", " ").trim();
        text = text.replaceAll("\\s+", " ");
        if (text.length() > MAX_MESSAGE_CHARS) {
            return text.substring(0, MAX_MESSAGE_CHARS).trim() + "…";
        }
        return text;
    }
}
