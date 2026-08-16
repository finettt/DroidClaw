package io.finett.droidclaw.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkerParameters;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;
import java.util.concurrent.ExecutionException;

import io.finett.droidclaw.model.ChatSession;
import io.finett.droidclaw.model.CronJob;
import io.finett.droidclaw.model.TaskResult;
import io.finett.droidclaw.repository.TaskRepository;

/**
 * Orchestration tests for {@link CronJobWorker}.
 *
 * <p>The real agent execution is replaced by a test double
 * (TestableCronJobWorker); everything else - job loading, enabled/paused
 * gating, stats bookkeeping, retry backoff scheduling and time-of-day
 * re-chaining - runs against real {@link TaskRepository} storage and a
 * test WorkManager.
 */
@RunWith(RobolectricTestRunner.class)
public class CronJobWorkerTest {

    private static final String JOB_ID = "job_1";
    /** CronJobScheduler work-name convention: cron_job_<id>. */
    private static final String WORK_NAME = "cron_job_" + JOB_ID;
    private static final String RETRY_WORK_NAME = "cron_job_retry_" + JOB_ID;

    private Context context;
    private TaskRepository repository;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        WorkManagerTestInitHelper.initializeTestWorkManager(context);
        repository = new TaskRepository(context);
    }

    /** Replaces the sandboxed agent run; the rest of the worker is real. */
    private static class TestableCronJobWorker extends CronJobWorker {

        TaskResult stubbedResult;
        RuntimeException stubbedException;

        TestableCronJobWorker(Context context, WorkerParameters params) {
            super(context, params);
        }

        @Override
        protected TaskResult executeWithSandbox(ChatSession session, String prompt) {
            if (stubbedException != null) {
                throw stubbedException;
            }
            return stubbedResult;
        }
    }

    private WorkerParameters paramsWithJobId(String jobId) {
        WorkerParameters params = mock(WorkerParameters.class, RETURNS_DEEP_STUBS);
        Data.Builder data = new Data.Builder();
        if (jobId != null) {
            data.putString("job_id", jobId);
        }
        when(params.getInputData()).thenReturn(data.build());
        return params;
    }

    private CronJob seedJob(String schedule) {
        CronJob job = new CronJob(JOB_ID, "Test job", "do something", schedule);
        repository.saveCronJob(job);
        return job;
    }

    private CronJob reloadJob() {
        return repository.getCronJob(JOB_ID);
    }

    private TaskResult successResult() {
        TaskResult result = new TaskResult(
                "result_1", TaskResult.TYPE_CRON_JOB, System.currentTimeMillis(), "done");
        result.putMetadata("status", "success");
        return result;
    }

    private TaskResult failureResult(String message) {
        TaskResult result = new TaskResult(
                "result_1", TaskResult.TYPE_CRON_JOB, System.currentTimeMillis(), message);
        result.putMetadata("status", "failure");
        return result;
    }

    private TestableCronJobWorker workerFor(String jobId, TaskResult result) {
        TestableCronJobWorker worker = new TestableCronJobWorker(context, paramsWithJobId(jobId));
        worker.stubbedResult = result;
        return worker;
    }

    private List<WorkInfo> uniqueWork(String workName)
            throws ExecutionException, InterruptedException {
        return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(workName).get();
    }

    // ==================== Input validation ====================

    @Test
    public void doWork_noJobId_returnsFailure() {
        TestableCronJobWorker worker = workerFor(null, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Failure);
    }

    @Test
    public void doWork_jobNotFound_returnsFailure() {
        TestableCronJobWorker worker = workerFor("missing_job", successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Failure);
    }

    @Test
    public void doWork_emptyPrompt_returnsFailure() {
        CronJob job = new CronJob(JOB_ID, "Test job", "   ", "hourly");
        repository.saveCronJob(job);

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Failure);
    }

    // ==================== Enabled / paused gating ====================

    @Test
    public void doWork_disabledJob_skipsWithoutExecuting() {
        CronJob job = seedJob("hourly");
        job.setEnabled(false);
        repository.updateCronJob(job);

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertEquals(0, reloadJob().getSuccessCount());
    }

    @Test
    public void doWork_pausedJob_skipsWithoutExecuting() {
        CronJob job = seedJob("hourly");
        job.setPaused(true);
        repository.updateCronJob(job);

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertEquals(0, reloadJob().getSuccessCount());
    }

    // ==================== Success bookkeeping ====================

    @Test
    public void doWork_success_recordsStats() {
        seedJob("hourly");

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);

        CronJob after = reloadJob();
        assertEquals(1, after.getSuccessCount());
        assertEquals(0, after.getFailureCount());
        assertEquals(0, after.getRetryCount());
        assertTrue(after.getLastRunTimestamp() > 0);
    }

    @Test
    public void doWork_success_timeOfDayJob_chainsNextOccurrence() throws Exception {
        seedJob("daily@08:00");

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);
        assertEquals(1, reloadJob().getSuccessCount());

        // The chain re-enqueues the next occurrence as one-time work
        // under the job's unique work name.
        List<WorkInfo> infos = uniqueWork(WORK_NAME);
        assertFalse("expected chained one-time work for the next occurrence", infos.isEmpty());
        assertEquals(WorkInfo.State.ENQUEUED, infos.get(0).getState());
    }

    // ==================== Failure & retry ====================

    @Test
    public void doWork_failure_recordsRetryAndSchedulesBackoff() throws Exception {
        seedJob("hourly");

        TestableCronJobWorker worker = workerFor(JOB_ID, failureResult("boom"));
        // The worker itself reports success to WorkManager - retries are
        // handled by the app's own backoff scheduler, not WorkManager's policy.
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);

        CronJob after = reloadJob();
        assertEquals(1, after.getRetryCount());
        assertEquals(1, after.getFailureCount());
        assertEquals(0, after.getSuccessCount());

        assertFalse("expected delayed retry work", uniqueWork(RETRY_WORK_NAME).isEmpty());
    }

    @Test
    public void doWork_failure_retriesExhausted_noFurtherRetryScheduled() throws Exception {
        CronJob job = seedJob("hourly");
        job.setRetryCount(job.getMaxRetries()); // already at the limit
        repository.updateCronJob(job);

        TestableCronJobWorker worker = workerFor(JOB_ID, failureResult("boom again"));
        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Success);

        assertEquals(job.getMaxRetries() + 1, reloadJob().getRetryCount());
        assertTrue("no retry work once retries are exhausted",
                uniqueWork(RETRY_WORK_NAME).isEmpty());
    }

    @Test
    public void doWork_executionThrows_returnsFailureAndRecordsJob() {
        seedJob("hourly");

        TestableCronJobWorker worker = workerFor(JOB_ID, successResult());
        worker.stubbedException = new RuntimeException("sandbox crash");

        assertTrue(worker.doWork() instanceof ListenableWorker.Result.Failure);

        CronJob after = reloadJob();
        assertEquals(1, after.getRetryCount());
        assertEquals(1, after.getFailureCount());
        assertTrue(after.getLastError() != null && after.getLastError().contains("sandbox crash"));
    }
}
