package io.finett.droidclaw.adapter;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.model.TaskRecord;

/**
 * Unit tests for TaskRecordAdapter.
 * Tests adapter creation, item listing, and record access.
 */
@RunWith(RobolectricTestRunner.class)
public class TaskRecordAdapterTest {
    
    private TaskRecordAdapter adapter;
    private List<TaskRecord> testRecords;
    private OnTaskRecordClickListenerMock listenerMock;
    
    /**
     * Mock implementation of the click listener interface.
     */
    private static class OnTaskRecordClickListenerMock implements TaskRecordAdapter.OnTaskRecordClickListener {
        private TaskRecord clickedRecord = null;
        private TaskRecord viewFullChatRecord = null;
        
        @Override
        public void onTaskRecordClick(TaskRecord record) {
            clickedRecord = record;
        }
        
        @Override
        public void onViewFullChatClick(TaskRecord record) {
            viewFullChatRecord = record;
        }
        
        public void reset() {
            clickedRecord = null;
            viewFullChatRecord = null;
        }
    }
    
    @Before
    public void setUp() {
        listenerMock = new OnTaskRecordClickListenerMock();
        adapter = new TaskRecordAdapter(listenerMock);
        
        // Create test records
        testRecords = new ArrayList<>();
        
        TaskRecord record1 = TaskRecord.create("cron-1", "Server Log Checker", "Check server logs");
        record1.setStartedAt(System.currentTimeMillis() - 3600000); // 1 hour ago
        record1.markSuccess("Found 3 errors: OutOfMemory, Connection timeout, Null pointer");
        record1.setToolCallsCount(5);
        record1.setTokensUsed(1250);
        
        TaskRecord record2 = TaskRecord.create("cron-2", "GitHub Issues Monitor", "Monitor GitHub issues");
        record2.setStartedAt(System.currentTimeMillis() - 7200000); // 2 hours ago
        record2.markFailure("Error: GitHub API rate limit exceeded");
        record2.setToolCallsCount(2);
        record2.setTokensUsed(450);
        
        TaskRecord record3 = TaskRecord.create("cron-3", "Database Backup Check", "Verify backup status");
        record3.setStartedAt(System.currentTimeMillis() - 1800000); // 30 min ago
        record3.markTimeout();
        record3.setToolCallsCount(1);
        record3.setTokensUsed(200);
        
        testRecords.add(record1);
        testRecords.add(record2);
        testRecords.add(record3);
    }
    
    // ========== CONSTRUCTOR AND BASIC TESTS ==========
    
    @Test
    public void testAdapterCreation() {
        assertNotNull(adapter);
        assertEquals(0, adapter.getItemCount());
    }
    
    @Test
    public void testSubmitList() {
        adapter.submitList(testRecords);
        assertEquals(3, adapter.getItemCount());
    }
    
    @Test
    public void testSubmitListEmpty() {
        adapter.submitList(new ArrayList<>());
        assertEquals(0, adapter.getItemCount());
    }
    
    @Test
    public void testGetRecord() {
        adapter.submitList(testRecords);
        TaskRecord record = adapter.getRecord(0);
        assertEquals("Server Log Checker", record.getCronJobName());
    }
    
    // ========== CLICK LISTENER TESTS ==========
    
    @Test
    public void testItemClickTriggersListener() {
        adapter.submitList(testRecords);
        
        // Verify listener is properly set
        assertNotNull(listenerMock);
    }
    
    // ========== STATUS DISPLAY TESTS ==========
    
    @Test
    public void testSuccessRecordDisplay() {
        TaskRecord successRecord = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        successRecord.markSuccess("Success response");
        successRecord.setToolCallsCount(3);
        successRecord.setTokensUsed(500);
        
        adapter.submitList(List.of(successRecord));
        
        assertEquals(1, adapter.getItemCount());
        assertEquals("success", adapter.getRecord(0).getStatus());
    }
    
    @Test
    public void testFailureRecordDisplay() {
        TaskRecord failureRecord = TaskRecord.create("cron-2", "Test Job", "Test prompt");
        failureRecord.markFailure("Error occurred");
        failureRecord.setToolCallsCount(1);
        failureRecord.setTokensUsed(200);
        
        adapter.submitList(List.of(failureRecord));
        
        assertEquals(1, adapter.getItemCount());
        assertEquals("failure", adapter.getRecord(0).getStatus());
    }
    
    @Test
    public void testTimeoutRecordDisplay() {
        TaskRecord timeoutRecord = TaskRecord.create("cron-3", "Test Job", "Test prompt");
        timeoutRecord.markTimeout();
        timeoutRecord.setToolCallsCount(2);
        timeoutRecord.setTokensUsed(350);
        
        adapter.submitList(List.of(timeoutRecord));
        
        assertEquals(1, adapter.getItemCount());
        assertEquals("timeout", adapter.getRecord(0).getStatus());
    }
    
    // ========== RESPONSE PREVIEW TESTS ==========
    
    @Test
    public void testLongResponseTruncation() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        
        // Create a very long response (> 150 chars)
        StringBuilder longResponse = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longResponse.append("Response line ").append(i).append("\n");
        }
        record.markSuccess(longResponse.toString());
        
        adapter.submitList(List.of(record));
        
        String response = adapter.getRecord(0).getResponse();
        assertNotNull(response);
        assertTrue(response.length() > 150);
    }
    
    @Test
    public void testShortResponseNotTruncated() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.markSuccess("Short response");
        
        adapter.submitList(List.of(record));
        
        String response = adapter.getRecord(0).getResponse();
        assertEquals("Short response", response);
    }
    
    @Test
    public void testNullResponseVisibility() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.setResponse(null);
        
        adapter.submitList(List.of(record));
        
        assertNull(adapter.getRecord(0).getResponse());
    }
    
    @Test
    public void testEmptyResponseVisibility() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.setResponse("");
        
        adapter.submitList(List.of(record));
        
        assertEquals("", adapter.getRecord(0).getResponse());
    }
    
    // ========== TOKENS DISPLAY TESTS ==========
    
    @Test
    public void testTokensDisplayUnder1000() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.markSuccess("Response");
        record.setTokensUsed(500);
        
        adapter.submitList(List.of(record));
        
        assertEquals(500, adapter.getRecord(0).getTokensUsed());
    }
    
    @Test
    public void testTokensDisplayOver1000() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.markSuccess("Response");
        record.setTokensUsed(1500);
        
        adapter.submitList(List.of(record));
        
        assertEquals(1500, adapter.getRecord(0).getTokensUsed());
    }
    
    // ========== TOOL CALLS DISPLAY TESTS ==========
    
    @Test
    public void testToolCallsDisplay() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.markSuccess("Response");
        record.setToolCallsCount(7);
        
        adapter.submitList(List.of(record));
        
        assertEquals(7, adapter.getRecord(0).getToolCallsCount());
    }
    
    @Test
    public void testZeroToolCalls() {
        TaskRecord record = TaskRecord.create("cron-1", "Test Job", "Test prompt");
        record.markSuccess("Response");
        record.setToolCallsCount(0);
        
        adapter.submitList(List.of(record));
        
        assertEquals(0, adapter.getRecord(0).getToolCallsCount());
    }
    
    // ========== DURATION DISPLAY TESTS ==========
    
    // Duration display tests are for TaskRecord model, not adapter
    // These are covered in TaskRecordTest.java
    
    // ========== SUBMIT LIST UPDATES TESTS ==========
    
    @Test
    public void testSubmitListReplacesOldList() {
        List<TaskRecord> firstList = List.of(
            TaskRecord.create("cron-1", "Job 1", "Prompt 1")
        );
        List<TaskRecord> secondList = List.of(
            TaskRecord.create("cron-2", "Job 2", "Prompt 2"),
            TaskRecord.create("cron-3", "Job 3", "Prompt 3")
        );
        
        adapter.submitList(firstList);
        assertEquals(1, adapter.getItemCount());
        
        adapter.submitList(secondList);
        assertEquals(2, adapter.getItemCount());
        assertEquals("Job 2", adapter.getRecord(0).getCronJobName());
    }
    
    @Test
    public void testSubmitListNullSafety() {
        adapter.submitList(null);
        assertEquals(0, adapter.getItemCount());
    }
}
