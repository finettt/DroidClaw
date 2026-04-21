package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class FileReadTool implements Tool {
    private static final String NAME = "read_file";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;

    public FileReadTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path relative to workspace root", true)
            .addInteger("offset", "Starting line number (0-based). Useful for large files.", false)
            .addInteger("limit", "Maximum number of lines to read. Default: all", false)
            .build();

        return new ToolDefinition(
            NAME,
            "Read the contents of a file from the virtual filesystem.",
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
    public ToolResult execute(JsonObject arguments) {
        try {
            if (!arguments.has("path")) {
                return ToolResult.error("Missing required argument: path");
            }

            String path = arguments.get("path").getAsString();
            Integer offset = arguments.has("offset") ? arguments.get("offset").getAsInt() : null;
            Integer limit = arguments.has("limit") ? arguments.get("limit").getAsInt() : null;

            VirtualFileSystem.FileReadResult result = vfs.readFile(path, offset, limit);

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("content", result.getContent());
            resultJson.addProperty("total_lines", result.getTotalLines());
            resultJson.addProperty("lines_read", result.getLinesRead());
            resultJson.addProperty("truncated", result.isTruncated());

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to read file: " + e.getMessage());
        }
    }
}