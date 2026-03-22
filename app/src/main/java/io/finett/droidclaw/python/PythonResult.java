package io.finett.droidclaw.python;

/**
 * Represents the result of a Python execution.
 * Contains the result value, output, error messages, and execution metadata.
 */
public class PythonResult {
    private final Object result;
    private final String output;
    private final String error;
    private final boolean success;
    private final long executionTimeMs;

    private PythonResult(Object result, String output, String error,
                         boolean success, long executionTimeMs) {
        this.result = result;
        this.output = output;
        this.error = error;
        this.success = success;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Creates a successful Python execution result.
     *
     * @param result         The return value from the Python code (may be null)
     * @param output         The stdout output from the execution
     * @param executionTime  Execution time in milliseconds
     * @return A successful PythonResult instance
     */
    public static PythonResult success(Object result, String output, long executionTime) {
        return new PythonResult(result, output, null, true, executionTime);
    }

    /**
     * Creates a failed Python execution result.
     *
     * @param error         The error message
     * @param executionTime Execution time in milliseconds
     * @return A failed PythonResult instance
     */
    public static PythonResult error(String error, long executionTime) {
        return new PythonResult(null, null, error, false, executionTime);
    }

    /**
     * Creates a failed Python execution result with output.
     * This is useful when partial output was captured before an error occurred.
     *
     * @param output        The partial stdout output
     * @param error         The error message
     * @param executionTime Execution time in milliseconds
     * @return A failed PythonResult instance with output
     */
    public static PythonResult errorWithOutput(String output, String error, long executionTime) {
        return new PythonResult(null, output, error, false, executionTime);
    }

    /**
     * @return The return value from the Python code, or null if none
     */
    public Object getResult() {
        return result;
    }

    /**
     * @return The stdout output from the execution
     */
    public String getOutput() {
        return output;
    }

    /**
     * @return The error message if execution failed, or null if successful
     */
    public String getError() {
        return error;
    }

    /**
     * @return Whether the execution was successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * @return Execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    @Override
    public String toString() {
        if (success) {
            return "PythonResult{success=true, executionTimeMs=" + executionTimeMs +
                    ", output='" + (output != null ? output : "") + "'}";
        } else {
            return "PythonResult{success=false, executionTimeMs=" + executionTimeMs +
                    ", error='" + error + "'}";
        }
    }
}