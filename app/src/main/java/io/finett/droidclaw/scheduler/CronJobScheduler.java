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

import java.util.Calendar;
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

        // daily@HH:MM / weekly@day@HH:MM run as a re-chaining one-time work,
        // because periodic work cannot pin execution to a wall-clock time.
        String normalized = job.getSchedule() == null
                ? "" : job.getSchedule().trim().toLowerCase(Locale.ROOT);
        if (isTimeOfDaySchedule(normalized) && scheduleTimeOfDayJob(job, normalized)) {
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

    /**
     * Enqueues the next occurrence of a time-of-day job as one-time work.
     * The chain continues when {@link CronJobWorker} re-invokes
     * {@link #scheduleJob(CronJob)} after a successful run.
     *
     * @return true if scheduled, false if the schedule could not be parsed
     *         (caller falls back to periodic scheduling)
     */
    private boolean scheduleTimeOfDayJob(CronJob job, String normalizedSchedule) {
        long now = System.currentTimeMillis();
        long nextRun = computeNextTimeOfDayRunMillis(normalizedSchedule, now);
        if (nextRun <= 0) {
            Log.w(TAG, "Unparseable time-of-day schedule, using periodic fallback: "
                    + job.getSchedule());
            return false;
        }

        String workName = getWorkName(job.getId());
        long delayMillis = Math.max(1L, nextRun - now);

        Data inputData = new Data.Builder()
                .putString("job_id", job.getId())
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(false)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CronJobWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(workName)
                .addTag("cron_job")
                .build();

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, workRequest);

        Log.d(TAG, "Time-of-day job scheduled: " + job.getId()
                + " (" + normalizedSchedule + ") next run in "
                + TimeUnit.MILLISECONDS.toMinutes(delayMillis) + " min");
        return true;
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

    public void executeJobWithDelay(String jobId, long delay, java.util.concurrent.TimeUnit unit) {
        String workName = "cron_job_retry_" + jobId;

        Data inputData = new Data.Builder()
                .putString("job_id", jobId)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CronJobWorker.class)
                .setInitialDelay(delay, unit)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(workName)
                .addTag("cron_job_retry")
                .build();

        workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.REPLACE,
                workRequest);

        Log.d(TAG, "Job queued for delayed retry in " + delay + " " + unit.name().toLowerCase() + ": " + jobId);
    }

    public static long computeRetryBackoffMinutes(int attempt) {
        int clamped = Math.min(Math.max(attempt, 0), 11);
        return Math.min(1L << clamped, java.util.concurrent.TimeUnit.DAYS.toMinutes(1));
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

    /**
     * True when the schedule pins execution to a wall-clock time:
     * {@code daily@HH:MM} or {@code weekly@day@HH:MM}, where day is a full
     * name ({@code monday}) or a three-letter code ({@code mon}).
     */
    public static boolean isTimeOfDaySchedule(String schedule) {
        if (schedule == null) return false;
        String s = schedule.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("daily@")) {
            return isValidTimeOfDay(s.substring("daily@".length()));
        }
        if (s.startsWith("weekly@")) {
            String rest = s.substring("weekly@".length());
            int at = rest.indexOf('@');
            if (at <= 0 || at >= rest.length() - 1) return false;
            return dayOfWeekFromName(rest.substring(0, at)) != -1
                    && isValidTimeOfDay(rest.substring(at + 1));
        }
        return false;
    }

    private static boolean isValidTimeOfDay(String time) {
        String[] parts = time.split(":");
        if (parts.length != 2 || parts[1].length() != 2) return false;
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int dayOfWeekFromName(String day) {
        switch (day) {
            case "sunday":
            case "sun": return Calendar.SUNDAY;
            case "monday":
            case "mon": return Calendar.MONDAY;
            case "tuesday":
            case "tue": return Calendar.TUESDAY;
            case "wednesday":
            case "wed": return Calendar.WEDNESDAY;
            case "thursday":
            case "thu": return Calendar.THURSDAY;
            case "friday":
            case "fri": return Calendar.FRIDAY;
            case "saturday":
            case "sat": return Calendar.SATURDAY;
            default: return -1;
        }
    }

    /**
     * Next occurrence strictly after {@code fromMillis} for a time-of-day
     * schedule, in epoch millis. Returns -1 if the schedule is not a valid
     * time-of-day schedule.
     *
     * <p>{@code daily@HH:MM}: today at HH:MM if still ahead, else tomorrow.
     * {@code weekly@day@HH:MM}: the next matching weekday at HH:MM.
     */
    public static long computeNextTimeOfDayRunMillis(String schedule, long fromMillis) {
        if (!isTimeOfDaySchedule(schedule)) return -1;
        String s = schedule.trim().toLowerCase(Locale.ROOT);

        int hour;
        int minute;
        int targetDayOfWeek = -1; // -1 = daily

        if (s.startsWith("daily@")) {
            String[] hm = s.substring("daily@".length()).split(":");
            hour = Integer.parseInt(hm[0]);
            minute = Integer.parseInt(hm[1]);
        } else {
            String rest = s.substring("weekly@".length());
            int at = rest.indexOf('@');
            targetDayOfWeek = dayOfWeekFromName(rest.substring(0, at));
            String[] hm = rest.substring(at + 1).split(":");
            hour = Integer.parseInt(hm[0]);
            minute = Integer.parseInt(hm[1]);
        }

        Calendar candidate = Calendar.getInstance();
        candidate.setTimeInMillis(fromMillis);
        candidate.set(Calendar.HOUR_OF_DAY, hour);
        candidate.set(Calendar.MINUTE, minute);
        candidate.set(Calendar.SECOND, 0);
        candidate.set(Calendar.MILLISECOND, 0);

        if (targetDayOfWeek != -1) {
            candidate.set(Calendar.DAY_OF_WEEK, targetDayOfWeek);
        }

        if (candidate.getTimeInMillis() <= fromMillis) {
            candidate.add(targetDayOfWeek != -1
                    ? Calendar.WEEK_OF_YEAR : Calendar.DAY_OF_YEAR, 1);
        }
        return candidate.getTimeInMillis();
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
