package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class FileDeleteTool implements Tool {
    private static final String NAME = "delete_file";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;

    public FileDeleteTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path relative to workspace root", true)
            .build();

        return new ToolDefinition(
            NAME,
            "Delete a file or empty directory from the virtual filesystem.",
            parameters
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
    public boolean requiresApproval() {
        return true;
    }

    
    @Override
    public String getApprovalDescription(JsonObject arguments) {
        String path = arguments.has("path") ?
            arguments.get("path").getAsString() : "unknown file";
        return "Delete file or directory:\n" + path;
    }

    @Override
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("path")) {
                return ToolResult.error("Missing required argument: path");
            }

            String path = arguments.get("path").getAsString();

            vfs.deleteFile(path);

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("deleted", true);

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to delete file: " + e.getMessage());
        }
    }
}