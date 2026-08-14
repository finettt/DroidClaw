package io.finett.droidclaw.tool.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Small lenient argument-extraction helpers shared by the calendar tools.
 * LLMs occasionally send numbers as strings, so numeric getters accept both.
 */
final class CalendarArgs {

    private CalendarArgs() {
    }

    /** Optional string; returns null when absent, null, or blank. */
    static String optString(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        String value = args.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    /** Required string; throws IllegalArgumentException with a tool-friendly message. */
    static String reqString(JsonObject args, String key) {
        String value = optString(args, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    /** Optional boolean; null when absent. */
    static Boolean optBoolean(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = args.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsBoolean();
        }
        return Boolean.parseBoolean(el.getAsString().trim());
    }

    /** Optional long (accepts JSON numbers or numeric strings); null when absent. */
    static Long optLong(JsonObject args, String key) {
        String value = optString(args, key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parameter " + key + " must be an integer, got: " + value);
        }
    }

    /** Optional array of integers (also accepts a comma-separated string); null when absent. */
    static List<Integer> optIntegerArray(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) {
            return null;
        }
        JsonElement el = args.get(key);
        List<Integer> result = new ArrayList<>();
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement item : arr) {
                if (item.isJsonNull()) continue;
                try {
                    result.add(Integer.parseInt(item.getAsString().trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Parameter " + key + " must contain only integers, got: " + item);
                }
            }
        } else {
            for (String part : el.getAsString().split(",")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    result.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Parameter " + key + " must contain only integers, got: " + trimmed);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }
}
