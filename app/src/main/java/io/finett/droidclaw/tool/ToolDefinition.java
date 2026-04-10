package io.finett.droidclaw.tool;

import com.google.gson.JsonObject;

/**
 * Represents a tool definition for the OpenAI function calling API.
 * Tools are defined in the Chat Completions API format.
 */
public class ToolDefinition {
    private final String type = "function";
    private final FunctionDefinition function;

    public ToolDefinition(String name, String description, JsonObject parameters) {
        this.function = new FunctionDefinition(name, description, parameters);
    }

    public String getType() {
        return type;
    }

    public FunctionDefinition getFunction() {
        return function;
    }

    /**
     * Converts this definition to a JSON object for the API.
     * 
     * @return JsonObject representation
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("function", function.toJson());
        return json;
    }

    /**
     * Inner class representing the function part of the tool definition.
     */
    public static class FunctionDefinition {
        private final String name;
        private final String description;
        private final JsonObject parameters;

        public FunctionDefinition(String name, String description, JsonObject parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public JsonObject getParameters() {
            return parameters;
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("description", description);
            json.add("parameters", parameters);
            json.addProperty("strict", true);
            return json;
        }
    }

    /**
     * Builder for creating tool parameter schemas.
     */
    public static class ParametersBuilder {
        private final JsonObject properties = new JsonObject();
        private final JsonObject schema = new JsonObject();

        public ParametersBuilder() {
            schema.addProperty("type", "object");
        }

        /**
         * Adds a string parameter.
         * 
         * @param name Parameter name
         * @param description Parameter description
         * @param required Whether the parameter is required
         * @return This builder
         */
        public ParametersBuilder addString(String name, String description, boolean required) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "string");
            prop.addProperty("description", description);
            properties.add(name, prop);
            
            if (required) {
                addRequired(name);
            }
            return this;
        }

        /**
         * Adds an integer parameter.
         * 
         * @param name Parameter name
         * @param description Parameter description
         * @param required Whether the parameter is required
         * @return This builder
         */
        public ParametersBuilder addInteger(String name, String description, boolean required) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "integer");
            prop.addProperty("description", description);
            properties.add(name, prop);
            
            if (required) {
                addRequired(name);
            }
            return this;
        }

        /**
         * Adds a boolean parameter.
         * 
         * @param name Parameter name
         * @param description Parameter description
         * @param required Whether the parameter is required
         * @return This builder
         */
        public ParametersBuilder addBoolean(String name, String description, boolean required) {
            JsonObject prop = new JsonObject();
            prop.addProperty("type", "boolean");
            prop.addProperty("description", description);
            properties.add(name, prop);
            
            if (required) {
                addRequired(name);
            }
            return this;
        }

        private void addRequired(String name) {
            if (!schema.has("required")) {
                schema.add("required", new com.google.gson.JsonArray());
            }
            schema.getAsJsonArray("required").add(name);
        }

        /**
         * Builds the parameters schema.
         * Adds additionalProperties: false for Structured Output compliance.
         *
         * @return JsonObject schema
         */
        public JsonObject build() {
            schema.add("properties", properties);
            schema.addProperty("additionalProperties", false);
            return schema;
        }
    }
}