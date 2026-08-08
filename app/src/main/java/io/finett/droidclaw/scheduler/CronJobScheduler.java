package io.finett.droidclaw.scheduler;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.worker.CronJobWorker;
import io.finett.droidclaw.worker.HeartbeatWorker;

public class CronJobScheduler {

    private static final String TAG = "CronJobScheduler";
    private static final String CRON_WORK_PREFIX = "cron_job_";

    private final Context appContext;
    private final WorkManager workManager;

    public CronJobScheduler(Context context) {
        this.appContext = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(appContext);
    }

    public void scheduleJob(CronJob job) {
        if (job == null) {
            Log.w(TAG, "Cannot schedule null job");
            return;
        }

        if (!job.isEnabled() || job.isPaused()) {
            Log.d(TAG, "Job is disabled or paused, not scheduling: " + job.getId());
            cancelJob(job.getId());
            return;
        }

        String workName = getWorkName(job.getId());
        long intervalMs = parseScheduleToInterval(job.getSchedule());

        Log.d(TAG, "Scheduling job: " + job.getId() + " with interval: " + intervalMs + "ms");

        Data inputData = new Data.Builder()
                .putString("job_id", job.getId())
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                CronJobWorker.class,
                intervalMs,
                TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(workName)
                .addTag("cron_job")
                .build();

        workManager.enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest);

        Log.d(TAG, "Job scheduled successfully: " + job.getId());
    }

    public void cancelJob(String jobId) {
        String workName = getWorkName(jobId);
        workManager.cancelUniqueWork(workName);
        Log.d(TAG, "Job cancelled: " + jobId);
    }

    public void executeJobNow(String jobId) {
        String workName = "cron_job_now_" + jobId;

        Data inputData = new Data.Builder()
                .putString("job_id", jobId)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CronJobWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(workName)
                .addTag("cron_job_manual")
                .build();

        workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest);

        Log.d(TAG, "Job queued for immediate execution: " + jobId);
    }

    public void cancelAllJobs() {
        workManager.cancelAllWorkByTag("cron_job");
        Log.d(TAG, "All cron jobs cancelled");
    }

    // ==================== Heartbeat ====================

    private static final String HEARTBEAT_WORK_NAME = "heartbeat_task";

    public void scheduleHeartbeat(HeartbeatConfig config) {
        if (!config.isEnabled()) {
            return;
        }

        long intervalMillis = Math.max(config.getIntervalMillis(), 15 * 60 * 1000L);
        long intervalMinutes = intervalMillis / (60 * 1000L);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean("enabled", config.isEnabled())
                .build();

        PeriodicWorkRequest heartbeatWork = new PeriodicWorkRequest.Builder(
                HeartbeatWorker.class,
                intervalMinutes,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        workManager.enqueueUniquePeriodicWork(
                HEARTBEAT_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                heartbeatWork
        );

        Log.d(TAG, "Heartbeat scheduled with interval: " + intervalMillis + "ms");
    }

    public void cancelHeartbeat() {
        workManager.cancelUniqueWork(HEARTBEAT_WORK_NAME);
        Log.d(TAG, "Heartbeat cancelled");
    }

    public void runHeartbeatNow() {
        String workName = "heartbeat_now_" + System.currentTimeMillis();

        Data inputData = new Data.Builder()
                .putBoolean("enabled", true)
                .build();

        OneTimeWorkRequest oneTimeWork = new OneTimeWorkRequest.Builder(HeartbeatWorker.class)
                .setInputData(inputData)
                .build();

        workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.APPEND,
                oneTimeWork
        );

        Log.d(TAG, "Heartbeat queued for immediate execution");
    }

    public static long parseScheduleToInterval(String schedule) {
        if (schedule == null || schedule.trim().isEmpty()) {
            return TimeUnit.HOURS.toMillis(1);
        }

        String normalized = schedule.trim().toLowerCase(Locale.ROOT);

        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            // not a number, parse as schedule string
        }

        if (normalized.equals("hourly")) {
            return TimeUnit.HOURS.toMillis(1);
        } else if (normalized.equals("daily")) {
            return TimeUnit.DAYS.toMillis(1);
        } else if (normalized.equals("weekly")) {
            return TimeUnit.DAYS.toMillis(7);
        } else if (normalized.startsWith("daily@")) {
            // WorkManager handles approximate daily timing
            return TimeUnit.DAYS.toMillis(1);
        } else if (normalized.startsWith("weekly@")) {
            return TimeUnit.DAYS.toMillis(7);
        } else if (normalized.startsWith("every_")) {
            return parseCustomInterval(normalized.substring(6));
        } else {
            return parseCronExpression(normalized);
        }
    }

    private static long parseCustomInterval(String interval) {
        String[] parts = interval.split("_");
        if (parts.length == 2) {
            try {
                int value = Integer.parseInt(parts[0]);
                String unit = parts[1];

                switch (unit) {
                    case "minute":
                    case "minutes":
                        return TimeUnit.MINUTES.toMillis(value);
                    case "hour":
                    case "hours":
                        return TimeUnit.HOURS.toMillis(value);
                    case "day":
                    case "days":
                        return TimeUnit.DAYS.toMillis(value);
                    default:
                        return TimeUnit.HOURS.toMillis(1);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid custom interval: " + interval);
            }
        }
        return TimeUnit.HOURS.toMillis(1);
    }

    private static long parseCronExpression(String cronExpression) {
        // Basic pattern matching for common cron schedules; full cron parsing is not supported
        if (cronExpression.startsWith("0 */")) {
            // Every N hours: "0 */2 * * *"
            try {
                String hoursStr = cronExpression.substring(4);
                int spaceIdx = hoursStr.indexOf(' ');
                if (spaceIdx > 0) {
                    hoursStr = hoursStr.substring(0, spaceIdx);
                }
                int hours = Integer.parseInt(hoursStr);
                return TimeUnit.HOURS.toMillis(hours);
            } catch (Exception e) {
                // Fall through
            }
        }

        if (cronExpression.startsWith("0 0 * * 0") || cronExpression.startsWith("0 0 * * 1")) {
            // Weekly (Sunday or Monday)
            return TimeUnit.DAYS.toMillis(7);
        }

        if (cronExpression.startsWith("0 0 *")) {
            // Daily at midnight
            return TimeUnit.DAYS.toMillis(1);
        }

        Log.w(TAG, "Unsupported cron expression, using 1 hour default: " + cronExpression);
        return TimeUnit.HOURS.toMillis(1);
    }

    public static String formatInterval(long intervalMs) {
        long hours = TimeUnit.MILLISECONDS.toHours(intervalMs);
        long days = hours / 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(intervalMs) % 60;

        if (days > 0) {
            if (days == 7) {
                return "Weekly";
            }
            return "Every " + days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return "Every " + hours + " hour" + (hours > 1 ? "s" : "");
        } else if (minutes > 0) {
            return "Every " + minutes + " minute" + (minutes > 1 ? "s" : "");
        } else {
            return "Every hour";
        }
    }

    public static String formatScheduleForDisplay(String schedule) {
        if (schedule == null || schedule.trim().isEmpty()) {
            return "Unknown";
        }

        String normalized = schedule.trim().toLowerCase(Locale.ROOT);

        if (normalized.equals("hourly")) {
            return "Every hour";
        } else if (normalized.equals("daily")) {
            return "Daily";
        } else if (normalized.equals("weekly")) {
            return "Weekly";
        } else if (normalized.startsWith("daily@")) {
            String time = normalized.substring(6);
            return "Daily at " + formatTime(time);
        } else if (normalized.startsWith("weekly@")) {
            String[] parts = normalized.substring(7).split("@");
            if (parts.length == 2) {
                String day = capitalizeFirst(parts[0]);
                String time = formatTime(parts[1]);
                return day + " at " + time;
            }
            return "Weekly";
        } else if (normalized.startsWith("every_")) {
            return formatInterval(parseScheduleToInterval(schedule));
        } else {
            try {
                long interval = Long.parseLong(normalized);
                return formatInterval(interval);
            } catch (NumberFormatException e) {
                // Cron expression
                return "Custom schedule";
            }
        }
    }

    public static String formatTime(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);

            String period = hour >= 12 ? "PM" : "AM";
            int displayHour = hour % 12;
            if (displayHour == 0) displayHour = 12;

            return String.format(Locale.US, "%d:%02d %s", displayHour, minute, period);
        } catch (Exception e) {
            return timeStr;
        }
    }

    static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    static String getWorkName(String jobId) {
        return CRON_WORK_PREFIX + jobId;
    }

    public void isJobScheduled(String jobId, ScheduledCallback callback) {
        String workName = getWorkName(jobId);
        workManager.getWorkInfosForUniqueWorkLiveData(workName)
                .observeForever(workInfos -> {
                    if (workInfos != null && !workInfos.isEmpty()) {
                        callback.onResult(true);
                    } else {
                        callback.onResult(false);
                    }
                });
    }

    public interface ScheduledCallback {
        void onResult(boolean isScheduled);
    }
}
