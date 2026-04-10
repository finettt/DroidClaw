package io.finett.droidclaw.tool.impl;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.finett.droidclaw.model.HeartbeatConfig;
import io.finett.droidclaw.repository.HeartbeatConfigRepository;
import io.finett.droidclaw.service.TaskScheduler;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolDefinition.ParametersBuilder;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Tool for setting up and configuring the heartbeat monitoring system.
 */
public class SetupHeartbeatTool implements Tool {

    private static final String TAG = "SetupHeartbeatTool";
    private static final String NAME = "setup_heartbeat";

    private final ToolDefinition definition;
    private final Context context;
    private HeartbeatConfigRepository configRepository;
    private TaskScheduler taskScheduler;
    private WorkspaceManager workspaceManager;

    public SetupHeartbeatTool(Context context) {
        this.context = context.getApplicationContext();
        this.definition = createDefinition();
    }

    private HeartbeatConfigRepository getConfigRepository() {
        if (configRepository == null) {
            configRepository = new HeartbeatConfigRepository(context);
        }
        return configRepository;
    }

    private TaskScheduler getTaskScheduler() {
        if (taskScheduler == null) {
            taskScheduler = new TaskScheduler(context);
        }
        return taskScheduler;
    }

    private WorkspaceManager getWorkspaceManager() {
        if (workspaceManager == null) {
            workspaceManager = new WorkspaceManager(context);
        }
        return workspaceManager;
    }

    private ToolDefinition createDefinition() {
        ParametersBuilder builder = new ParametersBuilder()
                .addString("interval", "Check interval: '15min', '30min', '1hour', '2hours' (default: '30min')", false)
                .addBoolean("enabled", "Whether to enable heartbeat monitoring (default: true)", false)
                .addString("monitoring_focus", "What to monitor (e.g., 'system health', 'file changes', 'memory usage'). Used to customize HEARTBEAT.md prompt", false);

        return new ToolDefinition(
                NAME,
                "Set up or reconfigure the heartbeat monitoring system. Heartbeat runs periodically " +
                        "to check system health and alert on issues. " +
                        "Interval options: '15min', '30min', '1hour', '2hours'. " +
                        "Monitoring focus customizes what the heartbeat checks. " +
                        "If enabled=true (default), heartbeat starts immediately.",
                builder.build()
        );
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            // Parse parameters with defaults
            boolean enabled = true;
            String interval = "30min";
            String monitoringFocus = null;

            if (arguments != null) {
                if (arguments.has("enabled")) {
                    enabled = arguments.get("enabled").getAsBoolean();
                }
                if (arguments.has("interval")) {
                    interval = arguments.get("interval").getAsString().trim().toLowerCase();
                }
                if (arguments.has("monitoring_focus")) {
                    monitoringFocus = arguments.get("monitoring_focus").getAsString().trim();
                }
            }

            // Parse interval
            long intervalMs = parseInterval(interval);
            if (intervalMs == -1) {
                return ToolResult.error(
                        "Invalid interval: '" + interval + "'. Valid options: '15min', '30min', '1hour', '2hours'"
                );
            }

            // Get or create config
            HeartbeatConfig config = getConfigRepository().getConfig();
            config.setIntervalMillis(intervalMs);
            config.setEnabled(enabled);
            getConfigRepository().updateConfig(config);

            // Update HEARTBEAT.md if monitoring focus is specified
            if (monitoringFocus != null && !monitoringFocus.isEmpty()) {
                updateHeartbeatPrompt(monitoringFocus);
            }

            // Schedule or cancel
            if (enabled) {
                getTaskScheduler().scheduleHeartbeat(config);
            } else {
                getTaskScheduler().cancelHeartbeat();
            }

            Log.d(TAG, "Heartbeat configured - enabled: " + enabled + ", interval: " + interval);

            // Build result
            JsonObject result = new JsonObject();
            result.addProperty("status", "configured");
            result.addProperty("enabled", enabled);
            result.addProperty("interval", interval);
            result.addProperty("interval_ms", intervalMs);
            if (monitoringFocus != null) {
                result.addProperty("monitoring_focus", monitoringFocus);
            }

            // Create human-readable message
            StringBuilder message = new StringBuilder();
            if (enabled) {
                message.append("✓ Heartbeat monitoring enabled\n");
                message.append("Interval: ").append(interval).append("\n");
                if (monitoringFocus != null) {
                    message.append("Focus: ").append(monitoringFocus).append("\n");
                }
                message.append("You will be notified if issues are detected.");
            } else {
                message.append("Heartbeat monitoring disabled");
            }

            result.addProperty("message", message.toString());

            return ToolResult.success(result);

        } catch (Exception e) {
            Log.e(TAG, "Failed to configure heartbeat", e);
            return ToolResult.error("Failed to configure heartbeat: " + e.getMessage());
        }
    }

    private long parseInterval(String interval) {
        switch (interval) {
            case "15min":
                return 15 * 60 * 1000L;
            case "30min":
                return 30 * 60 * 1000L;
            case "1hour":
                return 60 * 60 * 1000L;
            case "2hours":
                return 120 * 60 * 1000L;
            default:
                return -1;
        }
    }

    private void updateHeartbeatPrompt(String monitoringFocus) {
        try {
            File heartbeatFile = getWorkspaceManager().getHeartbeatFile();
            if (heartbeatFile != null && heartbeatFile.exists()) {
                String content = generateHeartbeatPrompt(monitoringFocus);
                try (FileWriter writer = new FileWriter(heartbeatFile)) {
                    writer.write(content);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to update HEARTBEAT.md, using existing file", e);
        }
    }

    private String generateHeartbeatPrompt(String monitoringFocus) {
        return "# Heartbeat Monitoring Configuration\n\n" +
                "## Focus Area\n" +
                monitoringFocus + "\n\n" +
                "## Instructions\n" +
                "Perform a system health check focusing on: " + monitoringFocus + ".\n\n" +
                "Check the following:\n" +
                "1. System status and any error logs\n" +
                "2. Recent task execution history\n" +
                "3. Resource usage patterns\n" +
                "4. Any anomalies or issues that need attention\n\n" +
                "If everything is normal, call the heartbeat_ok tool.\n" +
                "If you detect issues, describe what needs attention and DO NOT call heartbeat_ok.\n\n" +
                "## Report Format\n" +
                "Provide a concise status report:\n" +
                "- Overall health: OK or Issues Detected\n" +
                "- Key metrics or concerns\n" +
                "- Recommended actions (if any)";
    }
}
