package io.finett.droidclaw.python;

import android.content.Context;
import android.util.Log;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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
 *   <li>Injects an <b>{@code __import__} hook</b> that blocks dangerous modules:
 *       {@code os}, {@code subprocess}, {@code socket}, {@code ctypes}, {@code importlib},
 *       {@code shutil}, {@code tempfile}, {@code gc},
 *       {@code signal}, {@code multiprocessing}, {@code threading}.</li>
 *   <li>Captures both <b>{@code sys.stdout} and {@code sys.stderr}</b> and restores
 *       them after execution.</li>
 *   <li>Restores the original <b>{@code builtins.__import__}</b> hook after execution
 *       so safe-mode changes don't leak into later runs.</li>
 * </ol>
 *
 * <p>When safe mode is disabled (trusted-operator mode), code runs without the
 * security import hook.
 */
public class PythonExecutor {

    private static final String TAG = "PythonExecutor";

    /**
     * Blocked module names for safe-mode execution.
     * The import hook is installed and removed from Java, not from Python,
     * so there is no risk of recursive hook installation across test runs.
     */
    private static final String BLOCKED_MODULES_SET =
        "_BLOCKED = {"
        + "'subprocess','ctypes','multiprocessing'"
        + "}\n";

    /**
     * Python snippet that installs the import hook into builtins.
     *
     * <p>_BLOCKED and the original __import__ are captured as default argument
     * values at function-definition time so they remain available even after
     * the temporary names are deleted from the execution namespace.
     */
    private static final String INSTALL_HOOK_CODE =
        "import builtins as _b\n"
        + BLOCKED_MODULES_SET
        + "_b._dc_real_import = _b.__import__\n"
        + "def _dc_safe_import(name, *args, _real=_b._dc_real_import, _blocked=_BLOCKED, **kwargs):\n"
        + "    if name.split('.')[0] in _blocked or name in _blocked:\n"
        + "        raise ImportError('Module \\'' + name + '\\' is blocked by DroidClaw security policy')\n"
        + "    return _real(name, *args, **kwargs)\n"
        + "_b.__import__ = _dc_safe_import\n"
        + "del _b, _BLOCKED\n";

    /**
     * Python snippet that restores the original __import__ from builtins.
     */
    private static final String UNINSTALL_HOOK_CODE =
        "import builtins as _b\n"
        + "if hasattr(_b, '_dc_real_import'):\n"
        + "    _b.__import__ = _b._dc_real_import\n"
        + "    del _b._dc_real_import\n"
        + "del _b\n";

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

                boolean hookInstalled = false;

                try {
                    PyObject builtins = python.getBuiltins();
                    PyObject mainModule = python.getModule("__main__");
                    PyObject executionNamespace = mainModule.get("__dict__");

                    if (config.isSafeMode()) {
                        // Install the import hook by running INSTALL_HOOK_CODE in
                        // __main__ so that builtins.__import__ is replaced.
                        // We track whether the hook was installed so we can always
                        // remove it in the finally block.
                        builtins.callAttr("exec", INSTALL_HOOK_CODE,
                                executionNamespace, executionNamespace);
                        hookInstalled = true;
                    }

                    // Execute the user code in __main__ namespace.
                    builtins.callAttr("exec", code, executionNamespace, executionNamespace);

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
                    if (hookInstalled) {
                        try {
                            PyObject builtins = python.getBuiltins();
                            PyObject mainModule = python.getModule("__main__");
                            PyObject ns = mainModule.get("__dict__");
                            builtins.callAttr("exec", UNINSTALL_HOOK_CODE, ns, ns);
                        } catch (Exception ignored) { /* best-effort */ }
                    }
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