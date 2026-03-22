package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for writing content to files in the virtual filesystem.
 */
public class FileWriteTool implements Tool {
    private static final String NAME = "write_file";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;

    public FileWriteTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path relative to workspace root", true)
            .addString("content", "Content to write to the file", true)
            .addBoolean("append", "If true, append to existing file instead of overwriting. Default: false", false)
            .build();

        return new ToolDefinition(
            NAME,
            "Write content to a file in the virtual filesystem. Creates the file if it doesn't exist, overwrites if it does.",
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
            if (!arguments.has("content")) {
                return ToolResult.error("Missing required argument: content");
            }

            String path = arguments.get("path").getAsString();
            String content = arguments.get("content").getAsString();
            boolean append = arguments.has("append") && arguments.get("append").getAsBoolean();

            // Execute write operation
            vfs.writeFile(path, content, append);

            // Build result JSON
            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("bytes_written", content.getBytes(StandardCharsets.UTF_8).length);
            resultJson.addProperty("appended", append);
            resultJson.addProperty("success", true);

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to write file: " + e.getMessage());
        }
    }
}