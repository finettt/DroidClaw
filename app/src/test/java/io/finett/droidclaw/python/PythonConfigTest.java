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
        assertTrue("Safe mode should be enabled by default", config.isSafeMode());
        assertEquals("Default timeout should be 30 seconds", 30, config.getTimeoutSeconds());
        assertEquals("Default max output size should be 1MB", 1024 * 1024, config.getMaxOutputSize());
        assertNull("Python path should be null by default", config.getPythonPath());
    }

    @Test
    public void testSafeMode_enabledByDefault() {
        PythonConfig config = PythonConfig.createDefault();
        assertTrue("Safe mode must be on by default (blocks os, subprocess, socket, etc.)",
                config.isSafeMode());
    }

    @Test
    public void testSafeMode_canBeDisabledExplicitly() {
        PythonConfig config = PythonConfig.builder()
                .safeMode(false)
                .build();
        assertFalse("Safe mode should be disabled when explicitly set to false",
                config.isSafeMode());
    }

    @Test
    public void testBuilderWithCustomValues() {
        PythonConfig config = PythonConfig.builder()
                .enablePip(false)
                .safeMode(false)
                .timeout(60)
                .maxOutputSize(2048)
                .pythonPath("/custom/path")
                .build();

        assertFalse("Pip should be disabled", config.isPipEnabled());
        assertFalse("Safe mode should be disabled", config.isSafeMode());
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
                .safeMode(true)
                .timeout(45)
                .maxOutputSize(512 * 1024)
                .build();

        assertTrue("Pip should be enabled", config.isPipEnabled());
        assertTrue("Safe mode should be enabled", config.isSafeMode());
        assertEquals("Timeout should be 45 seconds", 45, config.getTimeoutSeconds());
        assertEquals("Max output size should be 512KB", 512 * 1024, config.getMaxOutputSize());
    }
}