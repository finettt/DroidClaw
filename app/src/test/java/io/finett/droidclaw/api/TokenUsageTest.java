package io.finett.droidclaw.api;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for TokenUsage class (Last Usage algorithm).
 */
public class TokenUsageTest {
    
    @Test
    public void testTokenUsageCreation() {
        TokenUsage usage = new TokenUsage(1000, 800, 200);
        
        assertEquals(1000, usage.getTotalTokens());
        assertEquals(800, usage.getPromptTokens());
        assertEquals(200, usage.getCompletionTokens());
        assertTrue(usage.isAvailable());
    }
    
    @Test
    public void testTokenUsageWithZeroTokens() {
        TokenUsage usage = new TokenUsage(0, 0, 0);
        
        assertEquals(0, usage.getTotalTokens());
        assertEquals(0, usage.getPromptTokens());
        assertEquals(0, usage.getCompletionTokens());
        assertFalse(usage.isAvailable());
    }
    
    @Test
    public void testTokenUsageAvailability() {
        TokenUsage available = new TokenUsage(100, 80, 20);
        TokenUsage notAvailable = new TokenUsage(0, 0, 0);
        
        assertTrue(available.isAvailable());
        assertFalse(notAvailable.isAvailable());
    }
    
    @Test
    public void testTokenUsageToString() {
        TokenUsage usage = new TokenUsage(1500, 1200, 300);
        String str = usage.toString();
        
        assertTrue(str.contains("1500"));
        assertTrue(str.contains("1200"));
        assertTrue(str.contains("300"));
    }
    
    @Test
    public void testLargeTokenCounts() {
        TokenUsage usage = new TokenUsage(100000, 80000, 20000);
        
        assertEquals(100000, usage.getTotalTokens());
        assertEquals(80000, usage.getPromptTokens());
        assertEquals(20000, usage.getCompletionTokens());
        assertTrue(usage.isAvailable());
    }
}