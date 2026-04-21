package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class FileListTool implements Tool {
    private static final String NAME = "list_files";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;
    private final SimpleDateFormat dateFormat;

    public FileListTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "Directory path relative to workspace root. Default: root", false)
            .addBoolean("recursive", "If true, list recursively. Default: false", false)
            .build();

        return new ToolDefinition(
            NAME,
            "List files and directories in a given path within the virtual filesystem.",
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
            String path = arguments.has("path") ? arguments.get("path").getAsString() : ".";
            boolean recursive = arguments.has("recursive") && arguments.get("recursive").getAsBoolean();

            VirtualFileSystem.FileListResult result = vfs.listFiles(path, recursive);

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("recursive", recursive);
            resultJson.addProperty("count", result.getFiles().size());

            JsonArray filesArray = new JsonArray();
            for (VirtualFileSystem.FileInfo fileInfo : result.getFiles()) {
                JsonObject fileJson = new JsonObject();
                fileJson.addProperty("path", fileInfo.getPath());
                fileJson.addProperty("type", fileInfo.isDirectory() ? "directory" : "file");
                fileJson.addProperty("size", fileInfo.getSize());
                fileJson.addProperty("modified", dateFormat.format(new Date(fileInfo.getLastModified())));
                filesArray.add(fileJson);
            }
            resultJson.add("files", filesArray);

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to list files: " + e.getMessage());
        }
    }
}