package io.finett.droidclaw.python;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Main Python execution wrapper using Chaquopy.
 * Provides methods to execute Python code strings and script files
 * with configurable timeouts and output capture.
 */
public class PythonExecutor {
    private static final String TAG = "PythonExecutor";

    private final Context context;
    private final PythonConfig config;
    private final ExecutorService executorService;
    private Python python;
    private boolean initialized = false;

    /**
     * Creates a PythonExecutor with the given context and configuration.
     *
     * @param context Android application context
     * @param config  Python execution configuration
     */
    public PythonExecutor(Context context, PythonConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Initializes the Python runtime. This is called lazily on first execution.
     */
    private synchronized void initializePython() {
        if (initialized) {
            return;
        }

        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context));
            }
            python = Python.getInstance();
            initialized = true;
            Log.i(TAG, "Python runtime initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Python runtime", e);
            throw new RuntimeException("Failed to initialize Python runtime: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures Python is initialized before execution.
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializePython();
        }
    }

    /**
     * Execute Python code string with default timeout.
     *
     * @param code Python code to execute
     * @return PythonResult containing execution result
     */
    public PythonResult executeCode(String code) {
        return executeCode(code, config.getTimeoutSeconds());
    }

    /**
     * Execute Python code with custom timeout.
     *
     * @param code           Python code to execute
     * @param timeoutSeconds Timeout in seconds
     * @return PythonResult containing execution result
     */
    public PythonResult executeCode(String code, int timeoutSeconds) {
        ensureInitialized();

        long startTime = System.currentTimeMillis();

        Future<PythonResult> future = executorService.submit(new Callable<PythonResult>() {
            @Override
            public PythonResult call() {
                try {
                    // Capture stdout
                    PyObject ioModule = python.getModule("io");
                    PyObject stringIO = ioModule.callAttr("StringIO");

                    // Redirect stdout
                    PyObject sysModule = python.getModule("sys");
                    PyObject originalStdout = sysModule.get("stdout");
                    sysModule.put("stdout", stringIO);

                    try {
                        // Execute the code
                        PyObject builtins = python.getBuiltins();
                        PyObject globals = python.getModule("__main__").get("__dict__");

                        // Create a new dictionary for local variables
                        PyObject locals = python.getModule("builtins").callAttr("dict");

                        // Execute the code
                        PyObject result = builtins.callAttr("exec", code, globals, locals);

                        // Get captured output
                        String output = stringIO.callAttr("getvalue").toString();

                        long executionTime = System.currentTimeMillis() - startTime;
                        return PythonResult.success(result, output, executionTime);

                    } finally {
                        // Restore stdout
                        sysModule.put("stdout", originalStdout);
                    }

                } catch (Exception e) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Python execution error", e);
                    return PythonResult.error(formatPythonError(e), executionTime);
                }
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            Log.w(TAG, "Python execution timed out after " + timeoutSeconds + " seconds");
            return PythonResult.error("Execution timed out after " + timeoutSeconds + " seconds", executionTime);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Python execution failed", e);
            return PythonResult.error("Execution failed: " + e.getMessage(), executionTime);
        }
    }

    /**
     * Execute Python script file with default timeout.
     *
     * @param scriptFile Python script file to execute
     * @return PythonResult containing execution result
     */
    public PythonResult executeScript(File scriptFile) {
        if (!scriptFile.exists() || !scriptFile.canRead()) {
            return PythonResult.error("Script file not found or not readable: " + scriptFile.getPath(), 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            // Read script content
            String code = readFile(scriptFile);
            return executeCode(code);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return PythonResult.error("Failed to read script: " + e.getMessage(), executionTime);
        }
    }

    /**
     * Install a package using pip at runtime.
     *
     * @param packageName Name of the package to install
     * @return PythonResult indicating success or failure
     */
    public PythonResult installPackage(String packageName) {
        if (!config.isPipEnabled()) {
            return PythonResult.error("Pip is disabled in configuration", 0);
        }

        ensureInitialized();

        long startTime = System.currentTimeMillis();

        Future<PythonResult> future = executorService.submit(new Callable<PythonResult>() {
            @Override
            public PythonResult call() {
                try {
                    // Use subprocess to run pip
                    PyObject subprocess = python.getModule("subprocess");
                    PyObject result = subprocess.callAttr(
                            "run",
                            new String[]{"pip", "install", packageName},
                            "capture_output", true,
                            "text", true
                    );

                    int returnCode = result.get("returncode").toInt();
                    String stdout = result.get("stdout").toString();
                    String stderr = result.get("stderr").toString();

                    long executionTime = System.currentTimeMillis() - startTime;

                    if (returnCode == 0) {
                        return PythonResult.success(null,
                                "Package installed: " + packageName + "\n" + stdout,
                                executionTime);
                    } else {
                        return PythonResult.error(
                                "Failed to install package: " + stderr,
                                executionTime);
                    }

                } catch (Exception e) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Package installation error", e);
                    return PythonResult.error("Failed to install package: " + e.getMessage(), executionTime);
                }
            }
        });

        try {
            // Use longer timeout for package installation
            return future.get(5, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            return PythonResult.error("Package installation timed out", executionTime);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return PythonResult.error("Package installation failed: " + e.getMessage(), executionTime);
        }
    }

    /**
     * Check if a package is installed.
     *
     * @param packageName Name of the package to check
     * @return true if the package is installed, false otherwise
     */
    public boolean isPackageInstalled(String packageName) {
        ensureInitialized();

        try {
            python.getModule(packageName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the Python version string.
     *
     * @return Python version string (e.g., "3.11.0")
     */
    public String getPythonVersion() {
        ensureInitialized();

        try {
            PyObject sysModule = python.getModule("sys");
            return sysModule.get("version").toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Read the contents of a file as a string.
     *
     * @param file File to read
     * @return File contents as string
     * @throws IOException if reading fails
     */
    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Format a Python error for display.
     *
     * @param e The exception from Python execution
     * @return Formatted error message
     */
    private String formatPythonError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return "Unknown Python error";
        }

        // Try to extract the most relevant part of the error
        try {
            BufferedReader reader = new BufferedReader(new StringReader(message));
            String line;
            StringBuilder formatted = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.contains("Error:") || line.contains("Exception:")) {
                    formatted.append(line).append("\n");
                }
            }
            if (formatted.length() > 0) {
                return formatted.toString().trim();
            }
        } catch (IOException ignored) {
            // Fall through to return the original message
        }

        return message;
    }

    /**
     * Shutdown the executor service.
     * Should be called when the executor is no longer needed.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "PythonExecutor shutdown complete");
    }
}