package io.finett.droidclaw.workflow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stage 1 of the load pipeline (spec §12): repairs the two legacy misspellings
 * from the original sketch and coerces unsafe scalar types, emitting a warning
 * for every change.
 *
 * <p>This exists because stage 2 deliberately rejects unknown properties, so
 * genuine typos in <em>new</em> fields are caught rather than ignored. The shim
 * applies to exactly two names and nothing else; it is compatibility, not a
 * feature, and always warns so files get fixed.
 */
public final class WorkflowNormalizer {

    /** Accepted-but-deprecated spellings, mapped to their correct name. */
    private static final Map<String, String> RENAMES = new java.util.LinkedHashMap<>();
    static {
        RENAMES.put("propmt", "prompt");
        RENAMES.put("max_truns", "max_turns");
    }

    /** Fields that may be dropped when their type is wrong, because they have a default. */
    private static final Set<String> DROPPABLE_INT_FIELDS =
            new HashSet<>(Arrays.asList("max_turns", "timeout_ms"));

    private WorkflowNormalizer() {}

    /**
     * Normalises a deep copy of {@code raw} in place and returns the warnings
     * produced. The input object is not modified.
     */
    public static JsonObject normalise(JsonObject raw, List<WorkflowIssue> warnings) {
        JsonObject copy = raw.deepCopy();
        JsonElement agentsEl = copy.get("agents");
        if (agentsEl != null && agentsEl.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : agentsEl.getAsJsonObject().entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonObject()) {
                    normaliseNode(e.getKey(), e.getValue().getAsJsonObject(), warnings);
                }
            }
        }
        return copy;
    }

    public static JsonObject normalise(JsonObject raw) {
        return normalise(raw, new ArrayList<WorkflowIssue>());
    }

    private static void normaliseNode(String key, JsonObject node, List<WorkflowIssue> warnings) {
        Set<String> renamed = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, String> rename : RENAMES.entrySet()) {
            String bad = rename.getKey();
            String good = rename.getValue();
            if (!node.has(bad)) continue;
            JsonElement value = node.remove(bad);
            if (node.has(good)) {
                // both present: keep the correct spelling, report the conflict
                warnings.add(WorkflowIssue.error(key, bad,
                        "both '" + good + "' and the misspelled '" + bad + "' are present; "
                                + "'" + bad + "' was ignored"));
                continue;
            }
            node.add(good, value);
            renamed.add(good);
            warnings.add(WorkflowIssue.warning(key, bad,
                    "'" + bad + "' is a misspelling of '" + good + "'; it was renamed automatically"));
        }

        // Type coercion applies ONLY to values this method just renamed. A field
        // the author spelled correctly keeps strict typing, so a genuine
        // `max_turns: 2.5` is still a hard error in stage 2 rather than being
        // silently dropped here.
        for (String field : DROPPABLE_INT_FIELDS) {
            if (!renamed.contains(field)) continue;
            if (!node.has(field)) continue;
            JsonElement v = node.get(field);
            if (isIntegral(v)) continue;
            node.remove(field);
            warnings.add(WorkflowIssue.warning(key, field,
                    field + " = " + shortJson(v) + " is not an integer; the field was dropped "
                            + "and the default will apply"));
        }
    }

    private static boolean isIntegral(JsonElement v) {
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) return false;
        double d = v.getAsDouble();
        return d == Math.floor(d) && !Double.isInfinite(d);
    }

    private static String shortJson(JsonElement v) {
        String s = v == null ? "null" : v.toString();
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
