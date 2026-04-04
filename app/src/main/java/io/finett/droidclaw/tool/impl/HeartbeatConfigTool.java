package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;
import io.finett.droidclaw.filesystem.WorkspaceManager;

/**
 * Tool for managing HEARTBEAT.md configuration file.
 * 
 * Allows the agent to create, update, or check the HEARTBEAT checklist
 * that guides heartbeat monitoring behavior.
 */
public class HeartbeatConfigTool implements Tool {
    private static final String TOOL_NAME = "heartbeat_config";
    private static final String ACTION_CREATE = "create";
    private static final String ACTION_UPDATE = "update";
    private static final String ACTION_SHOW = "show";

    private final WorkspaceManager workspaceManager;

    public HeartbeatConfigTool(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                TOOL_NAME,
                "Manage the HEARTBEAT.md checklist file. Create, update, or view the heartbeat monitoring configuration.",
                new ToolDefinition.ParametersBuilder()
                        .addString("action", "Action to perform: create, update, or show", true)
                        .addString("checklist", "Comma-separated checklist items (required for create/update)", false)
                        .build()
        );
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        String action = arguments.has("action") ? arguments.get("action").getAsString() : null;
        if (action == null) {
            return ToolResult.error("Missing required parameter: action. Valid actions: create, update, show");
        }

        String actionLower = action.toLowerCase();
        if (actionLower.equals(ACTION_CREATE)) {
            return createHeartbeat(arguments);
        } else if (actionLower.equals(ACTION_UPDATE)) {
            return updateHeartbeat(arguments);
        } else if (actionLower.equals(ACTION_SHOW)) {
            return showHeartbeat();
        } else {
            return ToolResult.error("Unknown action: " + action + ". Valid actions: create, update, show");
        }
    }

    private ToolResult createHeartbeat(JsonObject arguments) {
        File heartbeatFile = getHeartbeatFile();

        if (heartbeatFile.exists()) {
            return ToolResult.error("HEARTBEAT.md already exists. Use action='update' to modify it.");
        }

        String checklist = arguments.has("checklist") ? arguments.get("checklist").getAsString() : null;
        String content = generateDefaultHeartbeat(checklist);

        try {
            writeHeartbeatFile(content);
            return ToolResult.success("## HEARTBEAT.md Created\n\nCreated `.agent/HEARTBEAT.md` with your checklist configuration.\n\n**Checklist items:**\n" + formatChecklist(checklist));
        } catch (IOException e) {
            return ToolResult.error("Failed to create HEARTBEAT.md: " + e.getMessage());
        }
    }

    private ToolResult updateHeartbeat(JsonObject arguments) {
        File heartbeatFile = getHeartbeatFile();

        if (!heartbeatFile.exists()) {
            return ToolResult.error("HEARTBEAT.md does not exist. Use action='create' to create it first.");
        }

        String checklist = arguments.has("checklist") ? arguments.get("checklist").getAsString() : null;
        if (checklist == null || checklist.isEmpty()) {
            return ToolResult.error("Missing required parameter: checklist. Provide the updated checklist items.");
        }

        String content = generateDefaultHeartbeat(checklist);

        try {
            writeHeartbeatFile(content);
            return ToolResult.success("## HEARTBEAT.md Updated\n\nUpdated `.agent/HEARTBEAT.md` with new checklist.\n\n**Checklist items:**\n" + formatChecklist(checklist));
        } catch (IOException e) {
            return ToolResult.error("Failed to update HEARTBEAT.md: " + e.getMessage());
        }
    }

    private ToolResult showHeartbeat() {
        File heartbeatFile = getHeartbeatFile();

        if (!heartbeatFile.exists()) {
            return ToolResult.success("HEARTBEAT.md does not exist. Use action='create' to create one.");
        }

        try {
            String content = readFileContent(heartbeatFile);
            return ToolResult.success("## Current HEARTBEAT.md\n\n" + content);
        } catch (IOException e) {
            return ToolResult.error("Failed to read HEARTBEAT.md: " + e.getMessage());
        }
    }

    // Helper methods

    private File getHeartbeatFile() {
        File workspaceRoot = workspaceManager.getWorkspaceRoot();
        File agentDir = new File(workspaceRoot, ".agent");
        return new File(agentDir, "HEARTBEAT.md");
    }

    private String generateDefaultHeartbeat(String customChecklist) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Heartbeat Checklist\n\n");
        sb.append("Follow this checklist during each heartbeat check-in.\n\n");
        sb.append("## Checklist\n\n");

        if (customChecklist != null && !customChecklist.isEmpty()) {
            String[] items = customChecklist.split(",");
            for (String item : items) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    sb.append("- ").append(trimmed).append("\n");
                }
            }
        } else {
            sb.append("- Quick scan: anything urgent in emails or GitHub?\n");
            sb.append("- Check if any cron jobs failed\n");
            sb.append("- If daytime (8am-10pm), check if user needs anything\n");
            sb.append("- Monitor workspace for errors or warnings\n");
        }

        sb.append("\n**Remember:** If nothing needs attention, reply HEARTBEAT_OK.\n");

        return sb.toString();
    }

    private void writeHeartbeatFile(String content) throws IOException {
        File heartbeatFile = getHeartbeatFile();
        File parentDir = heartbeatFile.getParentFile();

        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (FileWriter writer = new FileWriter(heartbeatFile)) {
            writer.write(content);
        }
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        java.util.Scanner scanner = new java.util.Scanner(file);
        while (scanner.hasNextLine()) {
            content.append(scanner.nextLine()).append("\n");
        }
        scanner.close();
        return content.toString();
    }

    private String formatChecklist(String checklist) {
        if (checklist == null || checklist.isEmpty()) {
            return "- No items specified\n";
        }

        StringBuilder sb = new StringBuilder();
        String[] items = checklist.split(",");
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                sb.append("- ").append(trimmed).append("\n");
            }
        }
        return sb.toString();
    }
}
