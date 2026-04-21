package io.finett.droidclaw.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class HeartbeatResponse {

    private final boolean healthy;
    private final String summary;
    private final List<Issue> issues;

    public HeartbeatResponse(boolean healthy, String summary, List<Issue> issues) {
        this.healthy = healthy;
        this.summary = summary;
        this.issues = issues != null ? issues : new ArrayList<>();
    }

    public boolean isHealthy() {
        return healthy;
    }

    public String getSummary() {
        return summary;
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public static HeartbeatResponse fromJson(String jsonStr) {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        JsonObject json = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();

        if (!json.has("healthy")) {
            throw new IllegalArgumentException("Missing required field: healthy");
        }
        if (!json.has("summary")) {
            throw new IllegalArgumentException("Missing required field: summary");
        }
        if (!json.has("issues")) {
            throw new IllegalArgumentException("Missing required field: issues");
        }

        boolean healthy = json.get("healthy").getAsBoolean();
        String summary = json.get("summary").getAsString();

        List<Issue> issues = new ArrayList<>();
        JsonArray issuesArray = json.getAsJsonArray("issues");
        for (JsonElement element : issuesArray) {
            JsonObject issueObj = element.getAsJsonObject();
            String category = issueObj.has("category") ? issueObj.get("category").getAsString() : "unknown";
            String description = issueObj.has("description") ? issueObj.get("description").getAsString() : "";
            String severity = issueObj.has("severity") ? issueObj.get("severity").getAsString() : "low";
            issues.add(new Issue(category, description, severity));
        }

        return new HeartbeatResponse(healthy, summary, issues);
    }

    public static JsonObject getJsonSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", "Heartbeat health check response");

        JsonObject properties = new JsonObject();

        JsonObject healthyProp = new JsonObject();
        healthyProp.addProperty("type", "boolean");
        healthyProp.addProperty("description", "true if all systems are healthy, false otherwise");
        properties.add("healthy", healthyProp);

        JsonObject summaryProp = new JsonObject();
        summaryProp.addProperty("type", "string");
        summaryProp.addProperty("description", "Brief summary of system health status");
        properties.add("summary", summaryProp);

        JsonObject issuesProp = new JsonObject();
        issuesProp.addProperty("type", "array");
        issuesProp.addProperty("description", "List of any issues found during the health check");

        JsonObject issueItem = new JsonObject();
        issueItem.addProperty("type", "object");

        JsonObject issueProperties = new JsonObject();

        JsonObject categoryProp = new JsonObject();
        categoryProp.addProperty("type", "string");
        categoryProp.addProperty("description", "Category of the issue");
        issueProperties.add("category", categoryProp);

        JsonObject descriptionProp = new JsonObject();
        descriptionProp.addProperty("type", "string");
        descriptionProp.addProperty("description", "Description of the issue");
        issueProperties.add("description", descriptionProp);

        JsonObject severityProp = new JsonObject();
        severityProp.addProperty("type", "string");
        severityProp.addProperty("description", "Severity level");
        JsonArray severityEnum = new JsonArray();
        severityEnum.add("low");
        severityEnum.add("medium");
        severityEnum.add("high");
        severityProp.add("enum", severityEnum);
        issueProperties.add("severity", severityProp);

        issueItem.add("properties", issueProperties);

        JsonArray requiredFields = new JsonArray();
        requiredFields.add("category");
        requiredFields.add("description");
        requiredFields.add("severity");
        issueItem.add("required", requiredFields);

        issuesProp.add("items", issueItem);
        properties.add("issues", issuesProp);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("healthy");
        required.add("summary");
        required.add("issues");
        schema.add("required", required);

        return schema;
    }

    public static class Issue {
        private final String category;
        private final String description;
        private final String severity;

        public Issue(String category, String description, String severity) {
            this.category = category;
            this.description = description;
            this.severity = severity;
        }

        public String getCategory() {
            return category;
        }

        public String getDescription() {
            return description;
        }

        public String getSeverity() {
            return severity;
        }

        @Override
        public String toString() {
            return "[" + severity.toUpperCase() + "] " + category + ": " + description;
        }
    }
}
