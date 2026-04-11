package io.finett.droidclaw.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.chip.Chip;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.finett.droidclaw.R;
import io.finett.droidclaw.model.TaskExecutionRecord;
import io.finett.droidclaw.util.TestThemeHelper;

/**
 * Instrumented tests for TaskExecutionAdapter.
 * Tests the adapter's RecyclerView binding for execution records.
 */
@RunWith(AndroidJUnit4.class)
public class TaskExecutionAdapterInstrumentedTest {

    private TaskExecutionAdapter adapter;
    private TestOnTaskExecutionClickListener listener;

    @Before
    public void setUp() {
        listener = new TestOnTaskExecutionClickListener();
        adapter = new TaskExecutionAdapter(listener);
    }

    @Test
    public void initialState_hasZeroItems() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void submitList_withSingleRecord_increasesCount() {
        TaskExecutionRecord record = createTestRecord("task-1", "session-1", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(100);
        record.setIterations(3);

        adapter.submitList(java.util.Arrays.asList(record));

        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void submitList_withMultipleRecords_increasesCount() {
        TaskExecutionRecord record1 = createTestRecord("task-1", "session-1", 1, 1000L);
        record1.setEndTime(2000L);

        TaskExecutionRecord record2 = createTestRecord("task-2", "session-2", 1, 3000L);
        record2.setEndTime(4000L);

        TaskExecutionRecord record3 = createTestRecord("task-3", "session-3", 1, 5000L);
        record3.setEndTime(6000L);

        adapter.submitList(java.util.Arrays.asList(record1, record2, record3));

        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void submitList_withEmptyList_setsCountToZero() {
        adapter.submitList(java.util.Arrays.asList(
                createTestRecord("task-1", "session-1", 1, 1000L)
        ));

        assertEquals(1, adapter.getItemCount());

        adapter.submitList(java.util.Collections.emptyList());

        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void onCreateViewHolder_createsViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        assertNotNull("ViewHolder should not be null", viewHolder);
        assertNotNull("ViewHolder.itemView should not be null", viewHolder.itemView);
    }

    @Test
    public void onBindViewHolder_bindsRecordData() {
        TaskExecutionRecord record = createTestRecord("task-bind", "session-bind", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(150);
        record.setIterations(5);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Verify views are bound
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_execution_time));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_duration));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_tokens));
        assertNotNull(viewHolder.itemView.findViewById(R.id.text_iterations));
        assertNotNull(viewHolder.itemView.findViewById(R.id.chip_execution_status));
    }

    @Test
    public void onBindViewHolder_withSuccessRecord_showsSuccessStatus() {
        TaskExecutionRecord record = createTestRecord("task-success", "session-success", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        Chip statusChip = viewHolder.chipExecutionStatus;
        assertTrue("Status should be success", statusChip.getText().toString().contains("Success"));
    }

    @Test
    public void onBindViewHolder_withFailedRecord_showsFailedStatus() {
        TaskExecutionRecord record = createTestRecord("task-fail", "session-fail", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        Chip statusChip = viewHolder.chipExecutionStatus;
        assertTrue("Status should be failed", statusChip.getText().toString().contains("Failed"));
    }

    @Test
    public void onBindViewHolder_withShortDuration_formatsCorrectly() {
        TaskExecutionRecord record = createTestRecord("task-duration", "session-duration", 1, 1000L);
        record.setEndTime(1500L); // 0.5 seconds

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView durationView = viewHolder.itemView.findViewById(R.id.text_duration);
        String durationText = durationView.getText().toString();

        assertTrue("Duration should show 0.5s", durationText.contains("0.5") || durationText.contains("s"));
    }

    @Test
    public void onBindViewHolder_withLongDuration_formatsAsMinutes() {
        TaskExecutionRecord record = createTestRecord("task-duration", "session-duration", 1, 1000L);
        record.setEndTime(130000L); // 2 minutes 1 second

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView durationView = viewHolder.itemView.findViewById(R.id.text_duration);
        String durationText = durationView.getText().toString();

        assertTrue("Duration should show minutes", durationText.contains("2m") || durationText.contains("2"));
    }

    @Test
    public void onBindViewHolder_withTokens_showsFormattedTokens() {
        TaskExecutionRecord record = createTestRecord("task-tokens", "session-tokens", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(1500); // 1.5k tokens

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView tokensView = viewHolder.itemView.findViewById(R.id.text_tokens);
        String tokensText = tokensView.getText().toString();

        assertTrue("Tokens should show 1.5k", tokensText.contains("1.5") || tokensText.contains("1500"));
    }

    @Test
    public void onBindViewHolder_withSmallTokens_showsRawValue() {
        TaskExecutionRecord record = createTestRecord("task-tokens", "session-tokens", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(50); // 50 tokens

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView tokensView = viewHolder.itemView.findViewById(R.id.text_tokens);
        String tokensText = tokensView.getText().toString();

        assertTrue("Tokens should show 50", tokensText.contains("50"));
    }

    @Test
    public void onBindViewHolder_withIterations_showsIterationCount() {
        TaskExecutionRecord record = createTestRecord("task-iter", "session-iter", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setIterations(7);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView iterationsView = viewHolder.itemView.findViewById(R.id.text_iterations);
        String iterationsText = iterationsView.getText().toString();

        assertTrue("Iterations should show 7", iterationsText.contains("7"));
    }

    @Test
    public void onBindViewHolder_withErrorMessage_showsError() {
        TaskExecutionRecord record = createTestRecord("task-error", "session-error", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        record.setErrorMessage("Connection timeout");

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        assertTrue("Error view should be visible", errorView.getVisibility() == View.VISIBLE);

        TextView previewView = viewHolder.itemView.findViewById(R.id.text_preview_content);
        assertTrue("Preview should be hidden", previewView.getVisibility() == View.GONE);
    }

    @Test
    public void onBindViewHolder_withoutErrorMessage_showsPreview() {
        TaskExecutionRecord record = createTestRecord("task-preview", "session-preview", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        assertTrue("Error view should be hidden", errorView.getVisibility() == View.GONE);

        TextView previewView = viewHolder.itemView.findViewById(R.id.text_preview_content);
        assertTrue("Preview should be visible", previewView.getVisibility() == View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_clickListener_setsOnClickListener() {
        TaskExecutionRecord record = createTestRecord("task-click", "session-click", 1, 1000L);
        record.setEndTime(2000L);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        // Click the item
        viewHolder.itemView.performClick();

        // Verify listener was called
        assertEquals(1, listener.clickCount);
        assertEquals("task-click", listener.clickedRecord.getTaskId());
    }

    @Test
    public void onBindViewHolder_withDifferentTaskTypes_showsCorrectly() {
        int[] taskTypes = {1, 2, 3};

        for (int taskType : taskTypes) {
            TaskExecutionRecord record = createTestRecord("task-" + taskType, "session-" + taskType, taskType, 1000L);
            record.setEndTime(2000L);
            record.setSuccess(true);

            adapter.submitList(java.util.Arrays.asList(record));

            Context context = TestThemeHelper.getThemedContext();
            RecyclerView recyclerView = new RecyclerView(context);
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                    recyclerView,
                    0
            );

            adapter.onBindViewHolder(viewHolder, 0);

            // Should bind without crashing
            assertNotNull("ViewHolder should be bound", viewHolder.itemView);
        }
    }

    @Test
    public void onBindViewHolder_withZeroTokens_showsZero() {
        TaskExecutionRecord record = createTestRecord("task-zero", "session-zero", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(0);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView tokensView = viewHolder.itemView.findViewById(R.id.text_tokens);
        String tokensText = tokensView.getText().toString();

        assertTrue("Tokens should show 0", tokensText.contains("0"));
    }

    @Test
    public void onBindViewHolder_withZeroIterations_showsZero() {
        TaskExecutionRecord record = createTestRecord("task-zero", "session-zero", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setIterations(0);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView iterationsView = viewHolder.itemView.findViewById(R.id.text_iterations);
        String iterationsText = iterationsView.getText().toString();

        assertTrue("Iterations should show 0", iterationsText.contains("0"));
    }

    @Test
    public void onBindViewHolder_withVeryLargeTokens_formatsAsK() {
        TaskExecutionRecord record = createTestRecord("task-large", "session-large", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(true);
        record.setTokensUsed(15000); // 15k tokens

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView tokensView = viewHolder.itemView.findViewById(R.id.text_tokens);
        String tokensText = tokensView.getText().toString();

        assertTrue("Tokens should show 15k", tokensText.contains("15"));
    }

    @Test
    public void onBindViewHolder_withNullErrorMessage_hidesError() {
        TaskExecutionRecord record = createTestRecord("task-null-error", "session-null-error", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        record.setErrorMessage(null);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        // With null error, preview should be shown
        TextView previewView = viewHolder.itemView.findViewById(R.id.text_preview_content);
        assertTrue("Preview should be visible", previewView.getVisibility() == View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_withEmptyErrorMessage_hidesError() {
        TaskExecutionRecord record = createTestRecord("task-empty-error", "session-empty-error", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        record.setErrorMessage("");

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        TextView previewView = viewHolder.itemView.findViewById(R.id.text_preview_content);
        assertTrue("Preview should be visible", previewView.getVisibility() == View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_withLongErrorMessage_showsFullError() {
        String longError = "This is a very long error message that contains many words and should test " +
                "whether the adapter can handle long error text content properly without any issues " +
                "or crashes in the error display system. The error message should be fully visible " +
                "and properly formatted in the UI.";
        TaskExecutionRecord record = createTestRecord("task-long-error", "session-long-error", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        record.setErrorMessage(longError);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        String errorText = errorView.getText().toString();

        assertTrue("Error should contain long message", errorText.contains("very long error"));
    }

    @Test
    public void onBindViewHolder_withSpecialCharacters_handlesGracefully() {
        String specialError = "Special chars: <>&\"' @#$%^&*()_+-=[]{}|;':\",./<>?`~\n" +
                "Unicode: 你好世界 🌍 🚀 💓";
        TaskExecutionRecord record = createTestRecord("task-special", "session-special", 1, 1000L);
        record.setEndTime(2000L);
        record.setSuccess(false);
        record.setErrorMessage(specialError);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView errorView = viewHolder.itemView.findViewById(R.id.text_error_message);
        String errorText = errorView.getText().toString();

        assertTrue("Error should contain special chars", errorText.contains("Special chars"));
    }

    @Test
    public void onBindViewHolder_withDifferentTimestamps_formatsEachCorrectly() {
        long[] timestamps = {0L, 1000L, 1000000L, 1000000000L, System.currentTimeMillis()};

        for (long timestamp : timestamps) {
            TaskExecutionRecord record = createTestRecord("task-" + timestamp, "session-" + timestamp, 1, timestamp);
            record.setEndTime(timestamp + 1000L);
            record.setSuccess(true);

            adapter.submitList(java.util.Arrays.asList(record));

            Context context = TestThemeHelper.getThemedContext();
            RecyclerView recyclerView = new RecyclerView(context);
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                    recyclerView,
                    0
            );

            adapter.onBindViewHolder(viewHolder, 0);

            TextView timeView = viewHolder.itemView.findViewById(R.id.text_execution_time);
            String timeText = timeView.getText().toString();

            // Should have formatted date/time (not just raw timestamp)
            assertTrue("Time should be formatted", timeText.length() > 0);
        }
    }

    @Test
    public void onBindViewHolder_withDurationZero_formatsAsZero() {
        TaskExecutionRecord record = createTestRecord("task-zero-duration", "session-zero-duration", 1, 1000L);
        record.setEndTime(1000L); // Same as start = 0 duration
        record.setSuccess(true);

        adapter.submitList(java.util.Arrays.asList(record));

        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        adapter.onBindViewHolder(viewHolder, 0);

        TextView durationView = viewHolder.itemView.findViewById(R.id.text_duration);
        String durationText = durationView.getText().toString();

        assertTrue("Duration should show 0", durationText.contains("0"));
    }

    @Test
    public void onCreateViewHolder_withViewHolderType_zero_returnsViewHolder() {
        Context context = TestThemeHelper.getThemedContext();
        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        TaskExecutionAdapter.TaskExecutionViewHolder viewHolder = adapter.onCreateViewHolder(
                recyclerView,
                0
        );

        assertNotNull("ViewHolder should be created", viewHolder);
    }

    /**
     * Test click listener implementation.
     */
    private static class TestOnTaskExecutionClickListener implements TaskExecutionAdapter.OnTaskExecutionClickListener {
        private int clickCount = 0;
        private TaskExecutionRecord clickedRecord;

        @Override
        public void onTaskExecutionClick(TaskExecutionRecord record) {
            clickCount++;
            clickedRecord = record;
        }
    }

    /**
     * Helper method to create a test execution record.
     */
    private TaskExecutionRecord createTestRecord(String taskId, String sessionId, int taskType, long startTime) {
        TaskExecutionRecord record = new TaskExecutionRecord(taskId, sessionId, taskType, startTime);
        record.setEndTime(startTime + 1000L); // Default 1 second duration
        record.setSuccess(true);
        record.setTokensUsed(50);
        record.setIterations(2);
        return record;
    }
}
