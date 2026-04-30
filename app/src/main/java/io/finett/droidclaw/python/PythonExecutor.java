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
 * Executes Python code via Chaquopy with an optional security preamble.
 *
 * <p>When {@link PythonConfig#isSafeMode()} is true (the default), each execution:
 * <ol>
 *   <li>Uses a <b>fresh {@code dict()}</b> as globals — not {@code __main__.__dict__}.
 *       This eliminates cross-session state contamination.</li>
 *   <li>Injects an <b>{@code __import__} hook</b> that blocks dangerous modules:
 *       {@code os}, {@code subprocess}, {@code socket}, {@code ctypes}, {@code importlib},
 *       {@code shutil}, {@code tempfile}, {@code sys}, {@code builtins}, {@code gc},
 *       {@code signal}, {@code multiprocessing}, {@code threading}.</li>
 *   <li>Captures both <b>{@code sys.stdout} and {@code sys.stderr}</b> and restores
 *       them after execution.</li>
 * </ol>
 *
 * <p>When safe mode is disabled (trusted-operator mode), the original behaviour is used
 * ({@code __main__.__dict__} globals, no module blocking).
 */
public class PythonExecutor {

    private static final String TAG = "PythonExecutor";

    /**
     * Python preamble injected before user code in safe mode.
     * Sets up the __import__ hook that blocks dangerous modules.
     * Uses single-quotes inside to avoid Java string escaping issues.
     */
    private static final String SAFE_MODE_PREAMBLE =
        "import builtins as _builtins\n"
        + "_BLOCKED_MODULES = {\n"
        + "    'os', 'subprocess', 'socket', 'ctypes', 'importlib',\n"
        + "    'shutil', 'tempfile', 'sys', 'builtins', 'gc',\n"
        + "    'signal', 'multiprocessing', 'threading', 'pty',\n"
        + "    'fcntl', 'termios', 'tty', 'atexit', 'faulthandler',\n"
        + "}\n"
        + "_real_import = _builtins.__import__\n"
        + "def _safe_import(name, *args, **kwargs):\n"
        + "    top = name.split('.')[0]\n"
        + "    if top in _BLOCKED_MODULES or name in _BLOCKED_MODULES:\n"
        + "        raise ImportError(\n"
        + "            'Module \\'' + name + '\\' is blocked by DroidClaw security policy'\n"
        + "        )\n"
        + "    return _real_import(name, *args, **kwargs)\n"
        + "_builtins.__import__ = _safe_import\n"
        + "del _real_import, _builtins\n";

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

    // ==================== Initialisation ====================

    private synchronized void initializePython() {
        if (initialized) return;
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(context));
            }
            python = Python.getInstance();
            initialized = true;
            Log.i(TAG, "Python runtime initialised (safeMode=" + config.isSafeMode() + ")");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialise Python runtime", e);
            throw new RuntimeException("Failed to initialise Python runtime: " + e.getMessage(), e);
        }
    }

    private void ensureInitialized() {
        if (!initialized) initializePython();
    }

    // ==================== Code execution ====================

    public PythonResult executeCode(String code) {
        return executeCode(code, config.getTimeoutSeconds());
    }

    public PythonResult executeCode(String code, int timeoutSeconds) {
        ensureInitialized();

        final long startTime = System.currentTimeMillis();
        final String codeToRun = config.isSafeMode()
                ? SAFE_MODE_PREAMBLE + "\n" + code
                : code;

        Future<PythonResult> future = executorService.submit(new Callable<PythonResult>() {
            @Override
            public PythonResult call() {
                PyObject sysModule = python.getModule("sys");
                PyObject ioModule = python.getModule("io");

                // Capture stdout
                PyObject stdoutBuf = ioModule.callAttr("StringIO");
                PyObject originalStdout = sysModule.get("stdout");
                sysModule.put("stdout", stdoutBuf);

                // Capture stderr (fixes INFO-2: stderr was not captured before)
                PyObject stderrBuf = ioModule.callAttr("StringIO");
                PyObject originalStderr = sysModule.get("stderr");
                sysModule.put("stderr", stderrBuf);

                try {
                    PyObject builtins = python.getBuiltins();
                    PyObject globals;

                    if (config.isSafeMode()) {
                        // Fresh isolated globals dict — no __main__ contamination
                        globals = python.getModule("builtins").callAttr("dict");
                        // Seed with __builtins__ so basic built-ins work
                        globals.put("__builtins__", python.getModule("builtins"));
                    } else {
                        // Full access — shared __main__ globals (trusted operator mode)
                        globals = python.getModule("__main__").get("__dict__");
                    }

                    PyObject locals = python.getModule("builtins").callAttr("dict");

                    builtins.callAttr("exec", codeToRun, globals, locals);

                    String output = stdoutBuf.callAttr("getvalue").toString();
                    String errOutput = stderrBuf.callAttr("getvalue").toString();

                    long executionTime = System.currentTimeMillis() - startTime;

                    // Combine stderr into output if non-empty
                    String combinedOutput = output;
                    if (!errOutput.isEmpty()) {
                        combinedOutput = output.isEmpty()
                                ? "[stderr]\n" + errOutput
                                : output + "\n[stderr]\n" + errOutput;
                    }

                    return PythonResult.success(null, combinedOutput, executionTime);

                } catch (Exception e) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Python execution error", e);

                    // Capture any stderr written before the exception
                    String errOutput = "";
                    try {
                        errOutput = stderrBuf.callAttr("getvalue").toString();
                    } catch (Exception ignored) { /* best-effort */ }

                    String errorMsg = formatPythonError(e);
                    if (!errOutput.isEmpty()) {
                        errorMsg = errorMsg + "\n[stderr]\n" + errOutput;
                    }

                    return PythonResult.error(errorMsg, executionTime);
                } finally {
                    // Always restore stdout and stderr
                    try { sysModule.put("stdout", originalStdout); } catch (Exception ignored) { }
                    try { sysModule.put("stderr", originalStderr); } catch (Exception ignored) { }
                }
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            Log.w(TAG, "Python execution timed out after " + timeoutSeconds + " seconds");
            return PythonResult.error(
                    "Execution timed out after " + timeoutSeconds + " seconds", executionTime);
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Python execution failed", e);
            return PythonResult.error("Execution failed: " + e.getMessage(), executionTime);
        }
    }

    // ==================== Script execution ====================

    public PythonResult executeScript(File scriptFile) {
        if (!scriptFile.exists() || !scriptFile.canRead()) {
            return PythonResult.error(
                    "Script file not found or not readable: " + scriptFile.getPath(), 0);
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

    // ==================== Package management ====================

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
                    boolean installed = isPackageInstalled(packageName);
                    long executionTime = System.currentTimeMillis() - startTime;

                    if (installed) {
                        return PythonResult.success(null,
                                "Package already installed: " + packageName, executionTime);
                    } else {
                        return PythonResult.error(
                                "Package not available. Pre-install via build.gradle: "
                                + packageName, executionTime);
                    }
                } catch (Exception e) {
                    long executionTime = System.currentTimeMillis() - startTime;
                    Log.e(TAG, "Package installation error", e);
                    return PythonResult.error(
                            "Failed to install package: " + e.getMessage(), executionTime);
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
            return PythonResult.error(
                    "Package installation failed: " + e.getMessage(), executionTime);
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

    // ==================== Helpers ====================

    private String readFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
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
                    formatted.append(line).append('\n');
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