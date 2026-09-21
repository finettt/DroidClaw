package io.finett.droidclaw.workflow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the {@code {{ expr }}} template grammar defined in
 * {@code docs/features/workflow-schema.md} §8.2.
 *
 * <p>The grammar is deliberately tiny: no filters, no arithmetic, no string
 * methods. Keeping it small means the resolver is testable and cannot be abused
 * to build a programming language inside a JSON file.
 *
 * <p>An expression that does not resolve is an error, never an empty string —
 * silent empties produce plausible-looking garbage. The two documented
 * exceptions are encoded in {@link Mode}.
 */
public final class TemplateResolver {

    private static final Pattern EXPR = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*\\}\\}");
    private static final Set<String> WORKFLOW_FIELDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("goal", "input", "name")));

    /** Roots that are namespaces, not agent keys. */
    public static final Set<String> RESERVED_ROOTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("workflow", "inputs")));

    private TemplateResolver() {}

    /** How a missing or non-{@code ok} upstream output is treated. */
    public enum Mode {
        /**
         * Node templates. An unset output raises, unless the producing node
         * declared {@code on_error: "continue"}, in which case it degrades to
         * the empty string and {@code {{node.status}}} carries the truth.
         */
        STRICT,
        /**
         * The workflow's own {@code output}. A {@code skipped} node substitutes
         * the empty string (a conditional branch that legitimately did not fire
         * must not fail an otherwise-successful run); an {@code error} node
         * still raises, so results are never silently truncated.
         */
        FINAL
    }

    /** Raised when a template cannot be resolved. The message names the expression. */
    public static class TemplateException extends Exception {
        private final String expression;

        public TemplateException(String expression, String message) {
            super("{{" + expression + "}}: " + message);
            this.expression = expression;
        }

        public String getExpression() { return expression; }
    }

    /** One node's terminal result, as visible to templates. */
    public static final class NodeResult {
        private final WorkflowNodeStatus status;
        private final String output;
        private final JsonObject structured;

        public NodeResult(WorkflowNodeStatus status, String output, JsonObject structured) {
            this.status = status == null ? WorkflowNodeStatus.ERROR : status;
            this.output = output;
            this.structured = structured;
        }

        public static NodeResult ok(String output) {
            return new NodeResult(WorkflowNodeStatus.OK, output, null);
        }

        public static NodeResult ok(String output, JsonObject structured) {
            return new NodeResult(WorkflowNodeStatus.OK, output, structured);
        }

        public static NodeResult skipped() {
            return new NodeResult(WorkflowNodeStatus.SKIPPED, null, null);
        }

        public static NodeResult error(String partial) {
            return new NodeResult(WorkflowNodeStatus.ERROR, partial, null);
        }

        public WorkflowNodeStatus getStatus() { return status; }
        public String getOutput() { return output; }
        public JsonObject getStructured() { return structured; }
    }

    /** Everything a template may read while resolving. */
    public static final class Context {
        private String workflowName = "";
        private String workflowGoal = "";
        private String workflowInput = "";
        private final Map<String, NodeResult> nodes = new java.util.LinkedHashMap<>();
        private final Map<String, String> inputs = new java.util.LinkedHashMap<>();
        private final Set<String> tolerable = new HashSet<>();

        public Context workflowName(String v) { this.workflowName = nz(v); return this; }
        public Context workflowGoal(String v) { this.workflowGoal = nz(v); return this; }
        public Context workflowInput(String v) { this.workflowInput = nz(v); return this; }

        public Context node(String key, NodeResult result) {
            nodes.put(key, result);
            return this;
        }

        public Context input(String localName, String value) {
            inputs.put(localName, nz(value));
            return this;
        }

        public Context inputs(Map<String, String> values) {
            if (values != null) for (Map.Entry<String, String> e : values.entrySet()) inputs.put(e.getKey(), nz(e.getValue()));
            return this;
        }

        /**
         * Marks a node whose failure downstream templates may tolerate, because
         * it declared {@code on_error: "continue"}.
         */
        public Context tolerable(String key) {
            tolerable.add(key);
            return this;
        }

        public boolean hasNode(String key) { return nodes.containsKey(key); }
        public NodeResult getNode(String key) { return nodes.get(key); }
        public Set<String> nodeKeys() { return Collections.unmodifiableSet(nodes.keySet()); }

        private static String nz(String v) { return v == null ? "" : v; }
    }

    // ==================== resolution ====================

    /** Resolves with {@link Mode#STRICT} and no local inputs. */
    public static String resolve(String template, Context ctx) throws TemplateException {
        return resolve(template, ctx, Mode.STRICT);
    }

    public static String resolve(String template, Context ctx, Mode mode) throws TemplateException {
        if (template == null) return "";
        if (ctx == null) throw new IllegalArgumentException("context is required");
        Matcher m = EXPR.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String expr = m.group(1).trim();
            String value = evaluate(expr, ctx, mode);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluate(String expr, Context ctx, Mode mode) throws TemplateException {
        if (expr.isEmpty()) {
            throw new TemplateException(expr, "empty expression");
        }
        String[] parts = expr.split("\\.");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        String root = parts[0];

        if ("workflow".equals(root)) {
            if (parts.length != 2) throw new TemplateException(expr, "expected workflow.<goal|input|name>");
            switch (parts[1]) {
                case "goal":  return ctx.workflowGoal;
                case "input": return ctx.workflowInput;
                case "name":  return ctx.workflowName;
                default: throw new TemplateException(expr,
                        "'" + parts[1] + "' is not a workflow field (use goal, input or name)");
            }
        }

        if ("inputs".equals(root)) {
            if (parts.length != 2) throw new TemplateException(expr, "expected inputs.<name>");
            if (!ctx.inputs.containsKey(parts[1])) {
                throw new TemplateException(expr, "input '" + parts[1] + "' is not bound on this node");
            }
            return ctx.inputs.get(parts[1]);
        }

        // agent reference
        NodeResult r = ctx.nodes.get(root);
        if (r == null) {
            throw new TemplateException(expr, "agent '" + root + "' has no result (it did not run)");
        }
        if (parts.length < 2) {
            throw new TemplateException(expr, "expected " + root + ".output or " + root + ".status");
        }

        if ("status".equals(parts[1])) {
            return r.getStatus().jsonValue();
        }
        if (!"output".equals(parts[1])) {
            throw new TemplateException(expr,
                    "'" + parts[1] + "' is not valid after an agent name (use output or status)");
        }

        boolean okNode = r.getStatus() == WorkflowNodeStatus.OK;
        if (!okNode) {
            if (r.getStatus() == WorkflowNodeStatus.SKIPPED && mode == Mode.FINAL) return "";
            if (mode == Mode.STRICT && ctx.tolerable.contains(root)) return "";
            throw new TemplateException(expr, "agent '" + root + "' ended as "
                    + r.getStatus().jsonValue() + ", so it has no output");
        }

        if (parts.length == 2) {
            return r.getOutput() == null ? "" : r.getOutput();
        }
        if (parts.length != 3) {
            throw new TemplateException(expr, "too many path segments after output");
        }
        JsonObject s = r.getStructured();
        if (s == null) {
            throw new TemplateException(expr,
                    "agent '" + root + "' produced no structured output (it needs output_schema)");
        }
        JsonElement el = s.get(parts[2]);
        if (el == null || el.isJsonNull()) {
            throw new TemplateException(expr, "field '" + parts[2] + "' is absent from " + root + ".output");
        }
        return el.isJsonPrimitive() ? el.getAsString() : el.toString();
    }

    // ==================== static analysis ====================

    /** Every {@code {{...}}} expression body, in order of appearance. */
    public static List<String> expressions(String template) {
        List<String> out = new ArrayList<>();
        if (template == null) return out;
        Matcher m = EXPR.matcher(template);
        while (m.find()) out.add(m.group(1).trim());
        return out;
    }

    /**
     * Agent keys referenced by a template. Reserved roots ({@code workflow},
     * {@code inputs}) are excluded. Used to derive dependency edges — a template
     * reference is an edge (spec §8.2).
     */
    public static Set<String> referencedAgents(String template) {
        Set<String> out = new LinkedHashSet<>();
        for (String e : expressions(template)) {
            String root = e.split("\\.")[0].trim();
            if (!root.isEmpty() && !RESERVED_ROOTS.contains(root)) out.add(root);
        }
        return out;
    }

    /** Local input names referenced via {@code {{inputs.x}}}. */
    public static Set<String> referencedInputs(String template) {
        Set<String> out = new LinkedHashSet<>();
        for (String e : expressions(template)) {
            String[] p = e.split("\\.");
            if (p.length >= 2 && "inputs".equals(p[0].trim())) out.add(p[1].trim());
        }
        return out;
    }

    /**
     * Field names read from a given agent's structured output, i.e. every
     * {@code {{<agent>.output.<field>}}}. Used to check each field is backed by
     * that agent's {@code output_schema} (spec §12, rule 11).
     */
    public static Set<String> referencedFields(String template, String agentKey) {
        Set<String> out = new LinkedHashSet<>();
        for (String e : expressions(template)) {
            String[] p = e.split("\\.");
            if (p.length >= 3 && agentKey.equals(p[0].trim()) && "output".equals(p[1].trim())) {
                out.add(p[2].trim());
            }
        }
        return out;
    }

    /** True when {@code {{} and {@code }} counts differ — a malformed template. */
    public static boolean hasUnbalancedBraces(String template) {
        if (template == null) return false;
        return countOf(template, "{{") != countOf(template, "}}");
    }

    private static int countOf(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) { n++; i += sub.length(); }
        return n;
    }

    /** Reserved-root check helper used by the validator. */
    public static boolean isReservedRoot(String root) {
        return RESERVED_ROOTS.contains(root);
    }

    public static Set<String> workflowFields() { return WORKFLOW_FIELDS; }
}
