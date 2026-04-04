package io.finett.droidclaw.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskRecord;
import io.finett.droidclaw.model.TaskResult;

/**
 * Repository for managing CRON JOBS, task records, and task results.
 * 
 * Storage:
 * - CronJobs: Persistent list of scheduled tasks
 * - TaskRecords: Execution history for each cron job
 * - TaskResults: Results shown in Zen UI
 */
public class TaskRepository {
    private static final String PREFS_NAME = "task_repository";
    private static final String KEY_CRON_JOBS = "cron_jobs";
    private static final String KEY_TASK_RECORDS = "task_records";
    private static final String KEY_TASK_RESULTS = "task_results";
    
    private final SharedPreferences prefs;
    private final Gson gson;
    
    public TaskRepository(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    // ========== CRON JOBS ==========
    
    /**
     * Get all cron jobs.
     */
    public List<CronJob> getAllCronJobs() {
        String json = prefs.getString(KEY_CRON_JOBS, "[]");
        Type type = new TypeToken<List<CronJob>>(){}.getType();
        List<CronJob> jobs = gson.fromJson(json, type);
        return jobs != null ? jobs : new ArrayList<>();
    }
    
    /**
     * Get a specific cron job by ID.
     */
    public CronJob getCronJob(String cronJobId) {
        List<CronJob> jobs = getAllCronJobs();
        for (CronJob job : jobs) {
            if (job.getId().equals(cronJobId)) {
                return job;
            }
        }
        return null;
    }
    
    /**
     * Save or update a cron job.
     */
    public void saveCronJob(CronJob job) {
        List<CronJob> jobs = getAllCronJobs();
        
        // Remove existing if updating
        jobs.removeIf(j -> j.getId().equals(job.getId()));
        
        // Add updated job
        jobs.add(job);
        job.setUpdatedAt(System.currentTimeMillis());
        
        // Save to storage
        String json = gson.toJson(jobs);
        prefs.edit().putString(KEY_CRON_JOBS, json).apply();
    }
    
    /**
     * Delete a cron job and all its task records.
     */
    public void deleteCronJob(String cronJobId) {
        // Delete the job
        List<CronJob> jobs = getAllCronJobs();
        jobs.removeIf(j -> j.getId().equals(cronJobId));
        prefs.edit().putString(KEY_CRON_JOBS, gson.toJson(jobs)).apply();
        
        // Delete all task records for this job
        List<TaskRecord> records = getAllTaskRecords();
        records.removeIf(r -> r.getCronJobId().equals(cronJobId));
        prefs.edit().putString(KEY_TASK_RECORDS, gson.toJson(records)).apply();
    }
    
    /**
     * Get all enabled cron jobs.
     */
    public List<CronJob> getEnabledCronJobs() {
        return getAllCronJobs().stream()
            .filter(CronJob::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Toggle a cron job's enabled state.
     */
    public void toggleCronJob(String cronJobId) {
        CronJob job = getCronJob(cronJobId);
        if (job != null) {
            job.toggle();
            saveCronJob(job);
        }
    }
    
    // ========== TASK RECORDS ==========
    
    /**
     * Get all task records.
     */
    public List<TaskRecord> getAllTaskRecords() {
        String json = prefs.getString(KEY_TASK_RECORDS, "[]");
        Type type = new TypeToken<List<TaskRecord>>(){}.getType();
        List<TaskRecord> records = gson.fromJson(json, type);
        return records != null ? records : new ArrayList<>();
    }
    
    /**
     * Get a specific task record by ID.
     */
    public TaskRecord getTaskRecord(String recordId) {
        List<TaskRecord> records = getAllTaskRecords();
        for (TaskRecord record : records) {
            if (record.getId().equals(recordId)) {
                return record;
            }
        }
        return null;
    }
    
    /**
     * Get task records for a specific cron job.
     */
    public List<TaskRecord> getTaskRecordsForJob(String cronJobId) {
        return getAllTaskRecords().stream()
            .filter(r -> r.getCronJobId().equals(cronJobId))
            .sorted(Comparator.comparingLong(TaskRecord::getStartedAt).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Get recent task records (last N).
     */
    public List<TaskRecord> getRecentTaskRecords(int limit) {
        List<TaskRecord> records = getAllTaskRecords();
        Collections.sort(records, (a, b) -> Long.compare(b.getStartedAt(), a.getStartedAt()));
        return records.subList(0, Math.min(limit, records.size()));
    }
    
    /**
     * Save or update a task record.
     */
    public void saveTaskRecord(TaskRecord record) {
        List<TaskRecord> records = getAllTaskRecords();
        
        // Remove existing if updating
        records.removeIf(r -> r.getId().equals(record.getId()));
        
        // Add updated record
        records.add(record);
        
        // Save to storage
        String json = gson.toJson(records);
        prefs.edit().putString(KEY_TASK_RECORDS, json).apply();
    }
    
    /**
     * Delete old task records (keep only recent N per job).
     */
    public void pruneOldTaskRecords(int keepPerJob) {
        List<CronJob> jobs = getAllCronJobs();
        List<TaskRecord> allRecords = getAllTaskRecords();
        List<TaskRecord> recordsToKeep = new ArrayList<>();
        
        for (CronJob job : jobs) {
            List<TaskRecord> jobRecords = allRecords.stream()
                .filter(r -> r.getCronJobId().equals(job.getId()))
                .sorted(Comparator.comparingLong(TaskRecord::getStartedAt).reversed())
                .collect(Collectors.toList());
            
            // Keep only recent N records
            recordsToKeep.addAll(jobRecords.subList(0, Math.min(keepPerJob, jobRecords.size())));
        }
        
        prefs.edit().putString(KEY_TASK_RECORDS, gson.toJson(recordsToKeep)).apply();
    }
    
    // ========== TASK RESULTS ==========
    
    /**
     * Get all task results (for Zen UI).
     */
    public List<TaskResult> getAllTaskResults() {
        String json = prefs.getString(KEY_TASK_RESULTS, "[]");
        Type type = new TypeToken<List<TaskResult>>(){}.getType();
        List<TaskResult> results = gson.fromJson(json, type);
        return results != null ? results : new ArrayList<>();
    }
    
    /**
     * Get a specific task result by ID.
     */
    public TaskResult getTaskResult(String resultId) {
        List<TaskResult> results = getAllTaskResults();
        for (TaskResult result : results) {
            if (result.getId().equals(resultId)) {
                return result;
            }
        }
        return null;
    }
    
    /**
     * Save or update a task result.
     */
    public void saveTaskResult(TaskResult result) {
        List<TaskResult> results = getAllTaskResults();
        
        // Remove existing if updating
        results.removeIf(r -> r.getId().equals(result.getId()));
        
        // Add updated result
        results.add(result);
        
        // Save to storage
        String json = gson.toJson(results);
        prefs.edit().putString(KEY_TASK_RESULTS, json).apply();
    }
    
    /**
     * Get unviewed task results.
     */
    public List<TaskResult> getUnviewedResults() {
        return getAllTaskResults().stream()
            .filter(r -> !r.isUserViewed())
            .sorted(Comparator.comparingLong(TaskResult::getExecutedAt).reversed())
            .collect(Collectors.toList());
    }
    
    /**
     * Mark a task result as viewed.
     */
    public void markResultViewed(String resultId) {
        TaskResult result = getTaskResult(resultId);
        if (result != null) {
            result.markViewed();
            saveTaskResult(result);
        }
    }
    
    /**
     * Delete old task results (keep only recent N).
     */
    public void pruneOldTaskResults(int keepCount) {
        List<TaskResult> results = getAllTaskResults();
        Collections.sort(results, (a, b) -> Long.compare(b.getExecutedAt(), a.getExecutedAt()));
        
        List<TaskResult> toKeep = results.subList(0, Math.min(keepCount, results.size()));
        prefs.edit().putString(KEY_TASK_RESULTS, gson.toJson(toKeep)).apply();
    }
    
    // ========== STATISTICS ==========
    
    /**
     * Get total number of cron jobs.
     */
    public int getTotalCronJobs() {
        return getAllCronJobs().size();
    }
    
    /**
     * Get number of enabled cron jobs.
     */
    public int getEnabledCronJobsCount() {
        return (int) getAllCronJobs().stream().filter(CronJob::isEnabled).count();
    }
    
    /**
     * Get total executions across all jobs.
     */
    public int getTotalExecutions() {
        return getAllTaskRecords().size();
    }
    
    /**
     * Get success rate across all executions.
     */
    public int getOverallSuccessRate() {
        List<TaskRecord> records = getAllTaskRecords();
        if (records.isEmpty()) return 0;
        
        long successCount = records.stream().filter(TaskRecord::isSuccess).count();
        return (int) ((successCount * 100.0) / records.size());
    }
}