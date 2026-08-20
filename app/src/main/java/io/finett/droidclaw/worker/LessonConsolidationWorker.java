package io.finett.droidclaw.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;

import io.finett.droidclaw.agent.LessonConsolidator;
import io.finett.droidclaw.api.LlmApiService;
import io.finett.droidclaw.filesystem.WorkspaceManager;
import io.finett.droidclaw.repository.LessonConsolidationRepository;
import io.finett.droidclaw.repository.LessonRepository;
import io.finett.droidclaw.repository.MemoryRepository;
import io.finett.droidclaw.util.SettingsManager;

/**
 * Daily lesson consolidation worker (self-improvement layer ②).
 * Merges fresh lessons into MEMORY.md via LessonConsolidator, marks them
 * consumed, and journals the run. Failure keeps lessons unconsumed and
 * returns retry.
 */
public class LessonConsolidationWorker extends Worker {

    private static final String TAG = "LessonConsolidationWorker";

    public LessonConsolidationWorker(@NonNull Context context,
                                     @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SettingsManager settingsManager = new SettingsManager(getApplicationContext());
        if (!settingsManager.getAgentConfig().isLessonConsolidationEnabled()) {
            Log.d(TAG, "Lesson consolidation is disabled, skipping");
            return Result.success();
        }

        try {
            WorkspaceManager workspaceManager = new WorkspaceManager(getApplicationContext());
            File lessonsDir = new File(workspaceManager.getMemoryDirectory(), "lessons");
            LessonConsolidator consolidator = new LessonConsolidator(
                    new LlmApiService(settingsManager),
                    new MemoryRepository(workspaceManager),
                    new LessonRepository(lessonsDir),
                    new LessonConsolidationRepository(lessonsDir));

            boolean success = consolidator.runConsolidation();
            if (success) {
                settingsManager.setLessonConsolidationLastRunMillis(
                        System.currentTimeMillis());
                Log.i(TAG, "Lesson consolidation finished successfully");
                return Result.success();
            }
        } catch (Exception e) {
            Log.e(TAG, "Lesson consolidation crashed", e);
        }

        Log.w(TAG, "Lesson consolidation failed; will retry");
        return Result.retry();
    }
}