package io.finett.droidclaw.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.model.TaskResult;

/**
 * Repository for background task data.
 * Manages task results, cron jobs, and execution history using SharedPreferences.
 */
public class TaskRepository {
    private static final String TAG = "TaskRepository";
    private static final String PREFS_NAME = "droidclaw_tasks";

    // SharedPreferences keys
    private static final String KEY_TASK_RESULTS = "task_results";
    private static final String KEY_CRON_JOBS = "cron_jobs";
    private static final String KEY_EXECUTION_RECORDS = "execution_records";

    private final SharedPreferences prefs;

    public TaskRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ==================== TASK RESULTS ====================

    /**
     * Save a task result. Overwrites existing result with same ID.
     */
    public void saveTaskResult(TaskResult result) {
        try {
            List<TaskResult> results = getTaskResultsInternal();

            // Remove existing result with same ID
            results.removeIf(r -> r.getId().equals(result.getId()));
            results.add(result);

            saveTaskResultsInternal(results);
            Log.d(TAG, "Saved task result: " + result.getId());
        } catch (Exception e) {
            Log.e(TAG, "Error saving task result", e);
        }
    }

    /**
     * Get task results filtered by type, limited to specified count.
     * Returns results sorted by timestamp (newest first).
     */
    public List<TaskResult> getTaskResults(int type, int limit) {
        List<TaskResult> allResults = getTaskResultsInternal();
        List<TaskResult> filtered = new ArrayList<>();

        for (TaskResult result : allResults) {
            if (result.getType() == type) {
                filtered.add(result);
            }
        }

        // Sort by timestamp descending
        Collections.sort(filtered, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        // Apply limit
        if (limit > 0 && filtered.size() > limit) {
            return filtered.subList(0, limit);
        }

        return filtered;
    }

    /**
     * Get all task results without filtering.
     */
    public List<TaskResult> getAllTaskResults() {
        return getTaskResultsInternal();
    }

    /**
     * Get the most recent heartbeat task result.
     *
     * @return Latest heartbeat TaskResult or null if none exists
     */
    public TaskResult getLastHeartbeatResult() {
        List<TaskResult> heartbeatResults = getTaskResults(TaskResult.TYPE_HEARTBEAT, 1);
        if (heartbeatResults.isEmpty()) {
            return null;
        }
        return heartbeatResults.get(0);
    }

    /**
     * Delete a task result by ID.
     */
    public void deleteTaskResult(String id) {
        List<TaskResult> results = getTaskResultsInternal();
        results.removeIf(r -> r.getId().equals(id));
        saveTaskResultsInternal(results);
        Log.d(TAG, "Deleted task result: " + id);
    }

    private List<TaskResult> getTaskResultsInternal() {
        List<TaskResult> results = new ArrayList<>();

        try {
            String jsonString = prefs.getString(KEY_TASK_RESULTS, null);
            if (jsonString == null || jsonString.isEmpty()) {
                return results;
            }

            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                String id = jsonObject.getString("id");
                int type = jsonObject.getInt("type");
                long timestamp = jsonObject.getLong("timestamp");
                String content = jsonObject.optString("content", "");

                TaskResult result = new TaskResult(id, type, timestamp, content);

                // Load metadata
                if (jsonObject.has("metadata")) {
                    JSONObject metadataObj = jsonObject.getJSONObject("metadata");
                    Iterator<String> keys = metadataObj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        result.putMetadata(key, metadataObj.getString(key));
                    }
                }

                results.add(result);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading task results - clearing corrupted data", e);
            prefs.edit().remove(KEY_TASK_RESULTS).apply();
        }

        return results;
    }

    private void saveTaskResultsInternal(List<TaskResult> results) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (TaskResult result : results) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", result.getId());
                jsonObject.put("type", result.getType());
                jsonObject.put("timestamp", result.getTimestamp());
                jsonObject.put("content", result.getContent() != null ? result.getContent() : "");

                // Save metadata
                if (result.getMetadata() != null && !result.getMetadata().isEmpty()) {
                    JSONObject metadataObj = new JSONObject();
                    for (Map.Entry<String, String> entry : result.getMetadata().entrySet()) {
                        metadataObj.put(entry.getKey(), entry.getValue());
                    }
                    jsonObject.put("metadata", metadataObj);
                }

                jsonArray.put(jsonObject);
            }

            prefs.edit().putString(KEY_TASK_RESULTS, jsonArray.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving task results", e);
        }
    }

    // ==================== CRON JOBS ====================

    /**
     * Save a cron job. Overwrites existing job with same ID.
     */
    public void saveCronJob(CronJob job) {
        try {
            List<CronJob> jobs = getCronJobsInternal();

            // Remove existing job with same ID
            jobs.removeIf(j -> j.getId().equals(job.getId()));
            jobs.add(job);

            saveCronJobsInternal(jobs);
            Log.d(TAG, "Saved cron job: " + job.getId());
        } catch (Exception e) {
            Log.e(TAG, "Error saving cron job", e);
        }
    }

    /**
     * Get all cron jobs.
     */
    public List<CronJob> getCronJobs() {
        return getCronJobsInternal();
    }

    /**
     * Update an existing cron job.
     */
    public void updateCronJob(CronJob job) {
        saveCronJob(job);
        Log.d(TAG, "Updated cron job: " + job.getId());
    }

    /**
     * Delete a cron job by ID.
     */
    public void deleteCronJob(String id) {
        List<CronJob> jobs = getCronJobsInternal();
        jobs.removeIf(j -> j.getId().equals(id));
        saveCronJobsInternal(jobs);
        Log.d(TAG, "Deleted cron job: " + id);
    }

    /**
     * Get a specific cron job by ID.
     */
    public CronJob getCronJob(String id) {
        List<CronJob> jobs = getCronJobsInternal();
        for (CronJob job : jobs) {
            if (job.getId().equals(id)) {
                return job;
            }
        }
        return null;
    }

    private List<CronJob> getCronJobsInternal() {
        List<CronJob> jobs = new ArrayList<>();

        try {
            String jsonString = prefs.getString(KEY_CRON_JOBS, null);
            if (jsonString == null || jsonString.isEmpty()) {
                return jobs;
            }

            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                CronJob job = new CronJob();
                job.setId(jsonObject.optString("id", ""));
                job.setName(jsonObject.optString("name", ""));
                job.setPrompt(jsonObject.optString("prompt", ""));
                job.setSchedule(jsonObject.optString("schedule", ""));
                job.setEnabled(jsonObject.optBoolean("enabled", false));
                job.setLastRunTimestamp(jsonObject.optLong("lastRunTimestamp", 0));
                job.setModelReference(jsonObject.optString("modelReference", ""));

                jobs.add(job);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading cron jobs - clearing corrupted data", e);
            prefs.edit().remove(KEY_CRON_JOBS).apply();
        }

        return jobs;
    }

    private void saveCronJobsInternal(List<CronJob> jobs) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (CronJob job : jobs) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("id", job.getId());
                jsonObject.put("name", job.getName());
                jsonObject.put("prompt", job.getPrompt());
                jsonObject.put("schedule", job.getSchedule());
                jsonObject.put("enabled", job.isEnabled());
                jsonObject.put("lastRunTimestamp", job.getLastRunTimestamp());
                jsonObject.put("modelReference", job.getModelReference() != null ? job.getModelReference() : "");

                jsonArray.put(jsonObject);
            }

            prefs.edit().putString(KEY_CRON_JOBS, jsonArray.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving cron jobs", e);
        }
    }

    // ==================== EXECUTION RECORDS ====================

    /**
     * Save an execution record.
     */
    public void saveExecutionRecord(TaskExecutionRecord record) {
        try {
            List<TaskExecutionRecord> records = getExecutionRecordsInternal();
            records.add(record);

            saveExecutionRecordsInternal(records);
            Log.d(TAG, "Saved execution record for task: " + record.getTaskId());
        } catch (Exception e) {
            Log.e(TAG, "Error saving execution record", e);
        }
    }

    /**
     * Get execution history for a specific task.
     * Returns records sorted by start time (newest first).
     */
    public List<TaskExecutionRecord> getExecutionHistory(String taskId) {
        List<TaskExecutionRecord> allRecords = getExecutionRecordsInternal();
        List<TaskExecutionRecord> filtered = new ArrayList<>();

        for (TaskExecutionRecord record : allRecords) {
            if (record.getTaskId().equals(taskId)) {
                filtered.add(record);
            }
        }

        // Sort by start time descending
        Collections.sort(filtered, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));

        return filtered;
    }

    /**
     * Get all execution records.
     */
    public List<TaskExecutionRecord> getAllExecutionRecords() {
        return getExecutionRecordsInternal();
    }

    /**
     * Delete execution records for a specific task.
     */
    public void deleteExecutionRecords(String taskId) {
        List<TaskExecutionRecord> records = getExecutionRecordsInternal();
        records.removeIf(r -> r.getTaskId().equals(taskId));
        saveExecutionRecordsInternal(records);
        Log.d(TAG, "Deleted execution records for task: " + taskId);
    }

    /**
     * Clear all execution records.
     */
    public void clearAllExecutionRecords() {
        prefs.edit().remove(KEY_EXECUTION_RECORDS).apply();
        Log.d(TAG, "Cleared all execution records");
    }

    private List<TaskExecutionRecord> getExecutionRecordsInternal() {
        List<TaskExecutionRecord> records = new ArrayList<>();

        try {
            String jsonString = prefs.getString(KEY_EXECUTION_RECORDS, null);
            if (jsonString == null || jsonString.isEmpty()) {
                return records;
            }

            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                TaskExecutionRecord record = new TaskExecutionRecord();
                record.setTaskId(jsonObject.optString("taskId", ""));
                record.setSessionId(jsonObject.optString("sessionId", ""));
                record.setTaskType(jsonObject.optInt("taskType", 0));
                record.setStartTime(jsonObject.optLong("startTime", 0));
                record.setEndTime(jsonObject.optLong("endTime", 0));
                record.setDurationMillis(jsonObject.optLong("durationMillis", 0));
                record.setTokensUsed(jsonObject.optInt("tokensUsed", 0));
                record.setIterations(jsonObject.optInt("iterations", 0));
                record.setSuccess(jsonObject.optBoolean("success", false));
                record.setErrorMessage(jsonObject.optString("errorMessage", null));

                records.add(record);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error loading execution records - clearing corrupted data", e);
            prefs.edit().remove(KEY_EXECUTION_RECORDS).apply();
        }

        return records;
    }

    private void saveExecutionRecordsInternal(List<TaskExecutionRecord> records) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (TaskExecutionRecord record : records) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("taskId", record.getTaskId());
                jsonObject.put("sessionId", record.getSessionId());
                jsonObject.put("taskType", record.getTaskType());
                jsonObject.put("startTime", record.getStartTime());
                jsonObject.put("endTime", record.getEndTime());
                jsonObject.put("durationMillis", record.getDurationMillis());
                jsonObject.put("tokensUsed", record.getTokensUsed());
                jsonObject.put("iterations", record.getIterations());
                jsonObject.put("success", record.isSuccess());
                jsonObject.put("errorMessage", record.getErrorMessage() != null ? record.getErrorMessage() : "");

                jsonArray.put(jsonObject);
            }

            prefs.edit().putString(KEY_EXECUTION_RECORDS, jsonArray.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Error saving execution records", e);
        }
    }
}
