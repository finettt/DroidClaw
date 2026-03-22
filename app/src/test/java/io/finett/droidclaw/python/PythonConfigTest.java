package io.finett.droidclaw.python;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.*;

/**
 * Unit tests for PythonConfig.
 */
public class PythonConfigTest {

    @Test
    public void testDefaultConfig() {
        PythonConfig config = PythonConfig.createDefault();
        
        assertTrue("Pip should be enabled by default", config.isPipEnabled());
        assertEquals("Default timeout should be 30 seconds", 30, config.getTimeoutSeconds());
        assertEquals("Default max output size should be 1MB", 1024 * 1024, config.getMaxOutputSize());
        assertNull("Python path should be null by default", config.getPythonPath());
    }

    @Test
    public void testBuilderWithCustomValues() {
        PythonConfig config = PythonConfig.builder()
                .enablePip(false)
                .timeout(60)
                .maxOutputSize(2048)
                .pythonPath("/custom/path")
                .build();
        
        assertFalse("Pip should be disabled", config.isPipEnabled());
        assertEquals("Timeout should be 60 seconds", 60, config.getTimeoutSeconds());
        assertEquals("Max output size should be 2048", 2048, config.getMaxOutputSize());
        assertEquals("Python path should be set", "/custom/path", config.getPythonPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidTimeout() {
        PythonConfig.builder()
                .timeout(0)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithNegativeTimeout() {
        PythonConfig.builder()
                .timeout(-1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithInvalidMaxOutputSize() {
        PythonConfig.builder()
                .maxOutputSize(0)
                .build();
    }

    @Test
    public void testBuilderChaining() {
        PythonConfig config = PythonConfig.builder()
                .enablePip(true)
                .timeout(45)
                .maxOutputSize(512 * 1024)
                .build();
        
        assertTrue("Pip should be enabled", config.isPipEnabled());
        assertEquals("Timeout should be 45 seconds", 45, config.getTimeoutSeconds());
        assertEquals("Max output size should be 512KB", 512 * 1024, config.getMaxOutputSize());
    }
}