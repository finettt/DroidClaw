package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for getting file metadata and information.
 */
public class FileInfoTool implements Tool {
    private static final String NAME = "file_info";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;
    private final SimpleDateFormat dateFormat;

    public FileInfoTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path relative to workspace root", true)
            .build();

        return new ToolDefinition(
            NAME,
            "Get metadata and information about a file or directory (size, type, modification time, etc.).",
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
            // Extract arguments
            if (!arguments.has("path")) {
                return ToolResult.error("Missing required argument: path");
            }

            String path = arguments.get("path").getAsString();

            // Get file info
            VirtualFileSystem.FileInfo fileInfo = vfs.getFileInfo(path);

            // Build result JSON
            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", fileInfo.getPath());
            resultJson.addProperty("type", fileInfo.isDirectory() ? "directory" : "file");
            resultJson.addProperty("size", fileInfo.getSize());
            resultJson.addProperty("size_formatted", formatSize(fileInfo.getSize()));
            resultJson.addProperty("modified", fileInfo.getLastModified());
            resultJson.addProperty("modified_formatted", dateFormat.format(new Date(fileInfo.getLastModified())));
            resultJson.addProperty("exists", true);

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to get file info: " + e.getMessage());
        }
    }

    private String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.US, "%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.US, "%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
}