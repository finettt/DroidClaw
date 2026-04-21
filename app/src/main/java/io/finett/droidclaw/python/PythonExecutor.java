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

public class PythonExecutor {
    private static final String TAG = "PythonExecutor";

    private final Context context;
    private final PythonConfig config;
    private final ExecutorService executorService;
    private Python python;
    private boolean initialized = false;

    public PythonExecutor(Context context, PythonConfig config) {
        this.context = context.getApplicationContext();
        this.config = config;
        this.executorService = Executors.newSingleThreadExecutor();
    }

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

    private void ensureInitialized() {
        if (!initialized) {
            initializePython();
        }
    }

    public PythonResult executeCode(String code) {
        return executeCode(code, config.getTimeoutSeconds());
    }

    public PythonResult executeCode(String code, int timeoutSeconds) {
        ensureInitialized();

        long startTime = System.currentTimeMillis();

        Future<PythonResult> future = executorService.submit(new Callable<PythonResult>() {
            @Override
            public PythonResult call() {
                try {
                    PyObject ioModule = python.getModule("io");
                    PyObject stringIO = ioModule.callAttr("StringIO");

                    PyObject sysModule = python.getModule("sys");
                    PyObject originalStdout = sysModule.get("stdout");
                    sysModule.put("stdout", stringIO);

                    try {
                        PyObject builtins = python.getBuiltins();
                        PyObject globals = python.getModule("__main__").get("__dict__");
                        PyObject locals = python.getModule("builtins").callAttr("dict");

                        PyObject result = builtins.callAttr("exec", code, globals, locals);

                        String output = stringIO.callAttr("getvalue").toString();

                        long executionTime = System.currentTimeMillis() - startTime;
                        return PythonResult.success(result, output, executionTime);

                    } finally {
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

    public PythonResult executeScript(File scriptFile) {
        if (!scriptFile.exists() || !scriptFile.canRead()) {
            return PythonResult.error("Script file not found or not readable: " + scriptFile.getPath(), 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            String code = readFile(scriptFile);
            return executeCode(code);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            return PythonResult.error("Failed to read script: " + e.getMessage(), executionTime);
        }
    }

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
                    // Chaquopy has limited subprocess support; check importability instead
                    boolean installed = isPackageInstalled(packageName);
                    
                    long executionTime = System.currentTimeMillis() - startTime;

                    if (installed) {
                        return PythonResult.success(null,
                                "Package already installed: " + packageName,
                                executionTime);
                    } else {
                        // Packages must be pre-installed via build.gradle
                        return PythonResult.error(
                                "Package not available. Pre-install via build.gradle: " + packageName,
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

    public boolean isPackageInstalled(String packageName) {
        ensureInitialized();

        try {
            python.getModule(packageName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getPythonVersion() {
        ensureInitialized();

        try {
            PyObject sysModule = python.getModule("sys");
            return sysModule.get("version").toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

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

    private String formatPythonError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return "Unknown Python error";
        }

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
        }

        return message;
    }

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