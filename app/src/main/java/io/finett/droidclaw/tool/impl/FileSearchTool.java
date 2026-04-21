package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

public class FileSearchTool implements Tool {
    private static final String NAME = "search_files";
    private final VirtualFileSystem vfs;
    private final ToolDefinition definition;

    public FileSearchTool(VirtualFileSystem vfs) {
        this.vfs = vfs;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "Directory to search in (relative to workspace root)", false)
            .addString("pattern", "Regex pattern to search for in file contents", true)
            .addString("file_pattern", "Glob pattern to filter files (e.g., '*.txt')", false)
            .build();

        return new ToolDefinition(
            NAME,
            "Search for files by name pattern or search within file contents using regex.",
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
            if (!arguments.has("pattern")) {
                return ToolResult.error("Missing required argument: pattern");
            }

            String path = arguments.has("path") ? arguments.get("path").getAsString() : ".";
            String pattern = arguments.get("pattern").getAsString();
            String filePattern = arguments.has("file_pattern") ? arguments.get("file_pattern").getAsString() : null;

            VirtualFileSystem.FileSearchResult result = vfs.searchFiles(path, pattern, filePattern);

            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("pattern", pattern);
            if (filePattern != null) {
                resultJson.addProperty("file_pattern", filePattern);
            }
            resultJson.addProperty("matches_found", result.getMatches().size());

            JsonArray matchesArray = new JsonArray();
            for (VirtualFileSystem.SearchMatch match : result.getMatches()) {
                JsonObject matchJson = new JsonObject();
                matchJson.addProperty("file", match.getFile());
                matchJson.addProperty("line_number", match.getLineNumber());
                matchJson.addProperty("line", match.getLine());
                matchesArray.add(matchJson);
            }
            resultJson.add("matches", matchesArray);

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to search files: " + e.getMessage());
        }
    }
}