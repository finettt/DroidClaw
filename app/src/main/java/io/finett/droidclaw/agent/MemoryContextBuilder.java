package io.finett.droidclaw.agent;

import android.util.Log;

import java.io.IOException;

import io.finett.droidclaw.repository.MemoryRepository;

public class MemoryContextBuilder {
    private static final String TAG = "MemoryContextBuilder";
    
    private final MemoryRepository memoryRepository;
    
    public MemoryContextBuilder(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }
    
    public String buildMemoryContext() {
        StringBuilder context = new StringBuilder();
        
        try {
            String longTerm = memoryRepository.readLongTermMemory();
            if (!longTerm.isEmpty()) {
                context.append("# Long-term Memory\n\n");
                context.append(longTerm.trim()).append("\n\n");
                Log.d(TAG, "Loaded long-term memory: " + longTerm.length() + " chars");
            }
            
            String today = memoryRepository.readTodayNote();
            if (!today.isEmpty()) {
                context.append("# Today's Context\n\n");
                context.append(today.trim()).append("\n\n");
                Log.d(TAG, "Loaded today's note: " + today.length() + " chars");
            }
            
            String yesterday = memoryRepository.readYesterdayNote();
            if (!yesterday.isEmpty()) {
                context.append("# Yesterday's Context\n\n");
                context.append(yesterday.trim()).append("\n\n");
                Log.d(TAG, "Loaded yesterday's note: " + yesterday.length() + " chars");
            }
            
            if (context.length() == 0) {
                Log.d(TAG, "No memory context available");
                return "";
            }
            
            String wrapped = "--- MEMORY CONTEXT ---\n\n" + context.toString() + "--- END MEMORY CONTEXT ---\n";
            Log.d(TAG, "Built memory context: " + wrapped.length() + " total chars");
            return wrapped;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to build memory context", e);
            return "";
        }
    }
    
    public boolean hasMemory() {
        try {
            boolean hasLongTerm = memoryRepository.longTermMemoryExists();
            boolean hasToday = !memoryRepository.readTodayNote().isEmpty();
            boolean hasYesterday = !memoryRepository.readYesterdayNote().isEmpty();
            
            return hasLongTerm || hasToday || hasYesterday;
        } catch (IOException e) {
            Log.e(TAG, "Failed to check memory existence", e);
            return false;
        }
    }
}