package io.finett.droidclaw.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.finett.droidclaw.R;

/**
 * Helper class for managing notification permissions.
 * Handles runtime permission requests for Android 13+ (API 33+).
 */
public class NotificationPermissionHelper {

    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 1001;

    private final Context context;

    public NotificationPermissionHelper(Context context) {
        this.context = context;
    }

    /**
     * Check if notification permission is granted.
     * Returns true if permission is granted or not required (Android < 13).
     */
    public boolean hasNotificationPermission() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }

        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request notification permission if needed.
     * Returns true if permission is already granted, false if request was launched.
     */
    public boolean requestNotificationPermission(Activity activity) {
        if (hasNotificationPermission()) {
            return true;
        }


        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_CODE_NOTIFICATION_PERMISSION
        );
        return false;
    }

    /**
     * Check permission and show rationale dialog if needed.
     * Should be called before enabling notifications or scheduling tasks.
     */
    public void checkAndRequestPermission(Activity activity, PermissionCallback callback) {
        if (hasNotificationPermission()) {

            callback.onPermissionGranted();
            return;
        }


        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {

            showRationaleDialog(activity, callback);
        } else {

            requestNotificationPermission(activity);
        }
    }

    /**
     * Show rationale dialog explaining why we need notification permission.
     */
    private void showRationaleDialog(Activity activity, PermissionCallback callback) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.allow, (dialog, which) -> {
                    requestNotificationPermission(activity);
                })
                .setNegativeButton(R.string.not_now, (dialog, which) -> {
                    callback.onPermissionDenied();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Handle the result of the permission request.
     * Call this from Activity.onRequestPermissionsResult().
     */
    public void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, PermissionCallback callback) {
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                callback.onPermissionGranted();
            } else {
                callback.onPermissionDenied();
            }
        }
    }

    /**
     * Callback interface for permission request results.
     */
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
    }
}
