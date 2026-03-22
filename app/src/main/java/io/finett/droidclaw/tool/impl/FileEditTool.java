package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.finett.droidclaw.filesystem.VirtualFileSystem;
import io.finett.droidclaw.filesystem.PathValidator;
import io.finett.droidclaw.tool.Tool;
import io.finett.droidclaw.tool.ToolDefinition;
import io.finett.droidclaw.tool.ToolResult;

/**
 * Tool for editing files with search/replace, insert, and delete line operations.
 */
public class FileEditTool implements Tool {
    private static final String NAME = "edit_file";
    private final VirtualFileSystem vfs;
    private final PathValidator pathValidator;
    private final ToolDefinition definition;

    public FileEditTool(VirtualFileSystem vfs, PathValidator pathValidator) {
        this.vfs = vfs;
        this.pathValidator = pathValidator;
        this.definition = createDefinition();
    }

    private ToolDefinition createDefinition() {
        JsonObject parameters = new ToolDefinition.ParametersBuilder()
            .addString("path", "File path relative to workspace root", true)
            .addString("operation", "Type of edit operation: 'replace', 'insert', or 'delete_lines'", true)
            .addString("search", "For replace: text or regex pattern to search for", false)
            .addString("replacement", "For replace: text to replace matched content with", false)
            .addInteger("line_number", "For insert/delete_lines: line number to operate on (1-based)", false)
            .addString("content", "For insert: content to insert at specified line", false)
            .addInteger("count", "For delete_lines: number of lines to delete. Default: 1", false)
            .build();

        return new ToolDefinition(
            NAME,
            "Edit an existing file by performing search and replace operations, inserting lines, or deleting lines. More efficient than reading entire file, modifying, and writing back.",
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
            // Extract common arguments
            if (!arguments.has("path")) {
                return ToolResult.error("Missing required argument: path");
            }
            if (!arguments.has("operation")) {
                return ToolResult.error("Missing required argument: operation");
            }

            String path = arguments.get("path").getAsString();
            String operation = arguments.get("operation").getAsString();

            // Read the file
            File file = pathValidator.validateAndResolve(path);
            if (!file.exists() || !file.isFile()) {
                return ToolResult.error("File not found: " + path);
            }

            List<String> lines = readFileLines(file);

            // Perform the operation
            int changesCount = 0;
            switch (operation.toLowerCase()) {
                case "replace":
                    changesCount = performReplace(lines, arguments);
                    break;
                case "insert":
                    changesCount = performInsert(lines, arguments);
                    break;
                case "delete_lines":
                    changesCount = performDeleteLines(lines, arguments);
                    break;
                default:
                    return ToolResult.error("Invalid operation: " + operation + ". Must be 'replace', 'insert', or 'delete_lines'");
            }

            // Write the modified content back
            String newContent = String.join("\n", lines);
            vfs.writeFile(path, newContent, false);

            // Build result JSON
            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("path", path);
            resultJson.addProperty("operation", operation);
            resultJson.addProperty("changes_made", changesCount);
            resultJson.addProperty("total_lines", lines.size());

            return ToolResult.success(resultJson);

        } catch (SecurityException e) {
            return ToolResult.error("Security error: " + e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to edit file: " + e.getMessage());
        }
    }

    private List<String> readFileLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private int performReplace(List<String> lines, JsonObject arguments) {
        if (!arguments.has("search")) {
            throw new IllegalArgumentException("Missing required argument for replace: search");
        }
        if (!arguments.has("replacement")) {
            throw new IllegalArgumentException("Missing required argument for replace: replacement");
        }

        String search = arguments.get("search").getAsString();
        String replacement = arguments.get("replacement").getAsString();

        int changesCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            String originalLine = lines.get(i);
            String modifiedLine = originalLine.replace(search, replacement);
            if (!originalLine.equals(modifiedLine)) {
                lines.set(i, modifiedLine);
                changesCount++;
            }
        }

        return changesCount;
    }

    private int performInsert(List<String> lines, JsonObject arguments) {
        if (!arguments.has("line_number")) {
            throw new IllegalArgumentException("Missing required argument for insert: line_number");
        }
        if (!arguments.has("content")) {
            throw new IllegalArgumentException("Missing required argument for insert: content");
        }

        int lineNumber = arguments.get("line_number").getAsInt();
        String content = arguments.get("content").getAsString();

        // Convert to 0-based index
        int index = lineNumber - 1;

        if (index < 0 || index > lines.size()) {
            throw new IllegalArgumentException("Line number out of range: " + lineNumber);
        }

        lines.add(index, content);
        return 1;
    }

    private int performDeleteLines(List<String> lines, JsonObject arguments) {
        if (!arguments.has("line_number")) {
            throw new IllegalArgumentException("Missing required argument for delete_lines: line_number");
        }

        int lineNumber = arguments.get("line_number").getAsInt();
        int count = arguments.has("count") ? arguments.get("count").getAsInt() : 1;

        // Convert to 0-based index
        int startIndex = lineNumber - 1;

        if (startIndex < 0 || startIndex >= lines.size()) {
            throw new IllegalArgumentException("Line number out of range: " + lineNumber);
        }

        int actualCount = Math.min(count, lines.size() - startIndex);
        for (int i = 0; i < actualCount; i++) {
            lines.remove(startIndex);
        }

        return actualCount;
    }
}