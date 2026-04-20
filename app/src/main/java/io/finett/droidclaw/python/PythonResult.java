package io.finett.droidclaw.python;

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

    public static PythonResult success(Object result, String output, long executionTime) {
        return new PythonResult(result, output, null, true, executionTime);
    }

    public static PythonResult error(String error, long executionTime) {
        return new PythonResult(null, null, error, false, executionTime);
    }

    public static PythonResult errorWithOutput(String output, String error, long executionTime) {
        return new PythonResult(null, output, error, false, executionTime);
    }

    public Object getResult() {
        return result;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return success;
    }

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