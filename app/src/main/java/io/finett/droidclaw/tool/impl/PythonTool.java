package io.finett.droidclaw.tool.impl;

import android.content.Context;

import com.google.gson.JsonObject;

import java.io.File;

import io.finett.droidclaw.python.PythonConfig;
import io.finett.droidclaw.python.PythonExecutor;
import io.finett.droidclaw.python.PythonResult;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class PythonTool implements Tool {
    private static final String TOOL_NAME = "execute_python";

    private final PythonExecutor executor;
    private final File workspaceRoot;
    private final PythonConfig config;

    public PythonTool(Context context, File workspaceRoot) {
        this(context, workspaceRoot, PythonConfig.createDefault());
    }

    public PythonTool(Context context, File workspaceRoot, PythonConfig config) {
        this.workspaceRoot = workspaceRoot;
        this.config = config;
        this.executor = new PythonExecutor(context, config);
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }
    
    @Override
    public String getApprovalDescription(JsonObject arguments) {
        if (arguments.has("package")) {
            return "Install Python package via pip:\n" + arguments.get("package").getAsString();
        } else if (arguments.has("script_path")) {
            return "Execute Python script:\n" + arguments.get("script_path").getAsString();
        } else if (arguments.has("code")) {
            String code = arguments.get("code").getAsString();
            String preview = code.length() > 50 ? code.substring(0, 47) + "..." : code;
            return "Execute Python code:\n" + preview;
        }
        return "Execute Python operation";
    }

    @Override
    public ToolDefinition getDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
                .addString("code", "Python code to execute", false)
                .addString("script_path", "Path to Python script file (relative to workspace)", false)
                .addString("package", "Python package to install via pip", false)
                .addInteger("timeout_seconds", "Execution timeout in seconds (default: 30)", false)
                .build();

        return new ToolDefinition(
                TOOL_NAME,
                "Execute Python code or scripts. Can run Python code directly, execute script files, " +
                "or install packages via pip. Use for data processing, web scraping, calculations, " +
                "or any task that benefits from Python's ecosystem.",
                parameters
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            boolean hasCode = arguments.has("code") && !arguments.get("code").getAsString().isEmpty();
            boolean hasScript = arguments.has("script_path") && !arguments.get("script_path").getAsString().isEmpty();
            boolean hasPackage = arguments.has("package") && !arguments.get("package").getAsString().isEmpty();

            int modeCount = (hasCode ? 1 : 0) + (hasScript ? 1 : 0) + (hasPackage ? 1 : 0);
            if (modeCount == 0) {
                return ToolResult.error("Must provide one of: code, script_path, or package");
            }
            if (modeCount > 1) {
                return ToolResult.error("Can only provide one of: code, script_path, or package");
            }

            int timeout = config.getTimeoutSeconds();
            if (arguments.has("timeout_seconds") && !arguments.get("timeout_seconds").isJsonNull()) {
                timeout = arguments.get("timeout_seconds").getAsInt();
                if (timeout <= 0 || timeout > 300) {
                    return ToolResult.error("Timeout must be between 1 and 300 seconds");
                }
            }

            PythonResult result;

            if (hasPackage) {
                String packageName = arguments.get("package").getAsString();
                result = executor.installPackage(packageName);
            } else if (hasCode) {
                String code = arguments.get("code").getAsString();
                result = executor.executeCode(code, timeout);
            } else {
                String scriptPath = arguments.get("script_path").getAsString();
                File scriptFile = resolveScriptPath(scriptPath);

                if (scriptFile == null) {
                    return ToolResult.error("Invalid script path: " + scriptPath);
                }

                result = executor.executeScript(scriptFile);
            }

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("success", result.isSuccess());
            resultJson.addProperty("execution_time_ms", result.getExecutionTimeMs());

            if (result.isSuccess()) {
                if (result.getOutput() != null && !result.getOutput().isEmpty()) {
                    resultJson.addProperty("output", result.getOutput());
                }
                if (result.getResult() != null) {
                    resultJson.addProperty("result", result.getResult().toString());
                }
            } else {
                resultJson.addProperty("error", result.getError());
            }

            return ToolResult.success(resultJson);

        } catch (Exception e) {
            return ToolResult.error("Python execution error: " + e.getMessage());
        }
    }

    private File resolveScriptPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }

        // Reject absolute paths and directory traversal
        if (path.startsWith("/") || path.contains("/..") || path.startsWith("..") || path.contains("/../")) {
            return null;
        }

        File scriptFile = new File(workspaceRoot, path);

        if (!scriptFile.exists() || !scriptFile.isFile()) {
            return null;
        }

        // Ensure path is within workspace
        try {
            String canonicalWorkspace = workspaceRoot.getCanonicalPath();
            String canonicalScript = scriptFile.getCanonicalPath();

            if (!canonicalScript.startsWith(canonicalWorkspace)) {
                return null;
            }

            return scriptFile;
        } catch (Exception e) {
            return null;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}