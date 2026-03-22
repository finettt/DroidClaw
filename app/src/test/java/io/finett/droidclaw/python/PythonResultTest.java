package io.finett.droidclaw.python;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for PythonResult.
 */
public class PythonResultTest {

    @Test
    public void testSuccessResult() {
        Object resultObj = "test result";
        String output = "Hello, World!";
        long executionTime = 150L;
        
        PythonResult result = PythonResult.success(resultObj, output, executionTime);
        
        assertTrue("Result should be successful", result.isSuccess());
        assertEquals("Result object should match", resultObj, result.getResult());
        assertEquals("Output should match", output, result.getOutput());
        assertNull("Error should be null for success", result.getError());
        assertEquals("Execution time should match", executionTime, result.getExecutionTimeMs());
    }

    @Test
    public void testSuccessResultWithNullResult() {
        PythonResult result = PythonResult.success(null, "output", 100L);
        
        assertTrue("Result should be successful", result.isSuccess());
        assertNull("Result object should be null", result.getResult());
        assertEquals("Output should match", "output", result.getOutput());
    }

    @Test
    public void testErrorResult() {
        String error = "Test error message";
        long executionTime = 50L;
        
        PythonResult result = PythonResult.error(error, executionTime);
        
        assertFalse("Result should not be successful", result.isSuccess());
        assertNull("Result object should be null for error", result.getResult());
        assertNull("Output should be null for error", result.getOutput());
        assertEquals("Error should match", error, result.getError());
        assertEquals("Execution time should match", executionTime, result.getExecutionTimeMs());
    }

    @Test
    public void testErrorWithOutputResult() {
        String output = "Partial output before error";
        String error = "Test error";
        long executionTime = 75L;
        
        PythonResult result = PythonResult.errorWithOutput(output, error, executionTime);
        
        assertFalse("Result should not be successful", result.isSuccess());
        assertEquals("Output should match", output, result.getOutput());
        assertEquals("Error should match", error, result.getError());
    }

    @Test
    public void testToString() {
        PythonResult successResult = PythonResult.success("result", "output", 100L);
        String successString = successResult.toString();
        
        assertTrue("toString should contain 'success=true'", successString.contains("success=true"));
        assertTrue("toString should contain execution time", successString.contains("executionTimeMs=100"));
        
        PythonResult errorResult = PythonResult.error("error", 50L);
        String errorString = errorResult.toString();
        
        assertTrue("toString should contain 'success=false'", errorString.contains("success=false"));
        assertTrue("toString should contain error", errorString.contains("error='error'"));
    }
}