package io.finett.droidclaw.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Helper for the calendar runtime permissions ({@code READ_CALENDAR} and
 * {@code WRITE_CALENDAR}). Mirrors {@link NotificationPermissionHelper}.
 */
public class CalendarPermissionHelper {

    private static final int REQUEST_CODE_CALENDAR_PERMISSION = 1002;

    private final Context context;

    public CalendarPermissionHelper(Context context) {
        this.context = context;
    }

    /**
     * Static check usable from non-UI code (e.g. ToolRegistry registration).
     * Returns true when both calendar permissions are granted.
     */
    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Instance flavour of {@link #hasCalendarPermission(Context)}. */
    public boolean hasCalendarPermissions() {
        return hasCalendarPermission(context);
    }

    /**
     * Request the calendar permissions. Returns true when already granted,
     * false when the system dialog was launched.
     */
    public boolean requestCalendarPermissions(Activity activity) {
        if (hasCalendarPermissions()) {
            return true;
        }
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                },
                REQUEST_CODE_CALENDAR_PERMISSION
        );
        return false;
    }

    /**
     * Handle the permission request result. Call from
     * {@code Fragment.onRequestPermissionsResult()}.
     */
    public void handlePermissionResult(int requestCode, String[] permissions,
                                       int[] grantResults, PermissionCallback callback) {
        if (requestCode != REQUEST_CODE_CALENDAR_PERMISSION) {
            return;
        }
        boolean allGranted = grantResults.length > 0;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        if (allGranted) {
            callback.onPermissionGranted();
        } else {
            callback.onPermissionDenied();
        }
    }

    public interface PermissionCallback {
        void onPermissionGranted();

        void onPermissionDenied();
    }
}
