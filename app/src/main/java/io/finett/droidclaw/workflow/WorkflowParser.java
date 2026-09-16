package io.finett.droidclaw.workflow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stages 1 and 2 of the load pipeline (spec §12): normalise, then parse strictly.
 *
 * <p>Parsing is hand-written rather than Gson-reflective because the format
 * requires two things Gson does not do: rejecting unknown properties, and
 * distinguishing "field absent" from "field present but empty" — the difference
 * between <em>inherit every tool</em> and <em>no tools at all</em> (§9).
 *
 * <p>Cross-node rules (references resolve, no cycles, tools and models exist) are
 * stage 3 and live in {@link WorkflowValidator}, because they need a
 * {@link WorkflowEnvironment}.
 */
public final class WorkflowParser {

    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Pattern MODEL_REF = Pattern.compile("^[^/]+/.+$");
    private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern GUARD = Pattern.compile(
            "^\\{\\{\\s*[\\w.]+\\s*\\}\\}\\s*(==|!=|contains)\\s*'[^']*'$");

    private static final Set<String> TOP_KEYS = set("version", "name", "goal", "entry", "output", "defaults", "agents");
    private static final Set<String> DEFAULTS_KEYS = set("model", "max_turns", "timeout_ms", "allowed_tools",
            "denied_tools", "approval", "on_error", "retry", "max_parallel", "max_nodes");
    private static final Set<String> AGENT_KEYS = set("description", "model", "prompt", "allowed_tools",
            "denied_tools", "max_turns", "timeout_ms", "retry", "on_error", "approval", "output_schema",
            "depends_on", "inputs", "when");
    private static final Set<String> PROMPT_KEYS = set("system", "text", "template", "from_agent");
    private static final Set<String> RETRY_KEYS = set("max_attempts", "backoff_ms");

    public static final int MIN_MAX_TURNS = 1;
    public static final int MAX_MAX_TURNS = 200;
    public static final int MIN_TIMEOUT_MS = 1000;

    private WorkflowParser() {}

    private static Set<String> set(String... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    /** Outcome of a parse: a workflow when it succeeded, plus every issue found. */
    public static final class Result {
        private final Workflow workflow;
        private final WorkflowValidationResult issues;

        Result(Workflow workflow, WorkflowValidationResult issues) {
            this.workflow = workflow;
            this.issues = issues;
        }

        /** Non-null only when there were no errors. */
        public Workflow getWorkflow() { return workflow; }
        public WorkflowValidationResult getIssues() { return issues; }
        public boolean isSuccess() { return workflow != null && issues.isValid(); }
    }

    // ==================== entry points ====================

    public static Result parse(String json) {
        WorkflowValidationResult res = new WorkflowValidationResult();
        if (json == null || json.trim().isEmpty()) {
            res.add(WorkflowIssue.error("workflow file is empty"));
            return new Result(null, res);
        }
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > Workflow.MAX_FILE_BYTES) {
            res.add(WorkflowIssue.error("workflow file is " + bytes + " bytes, over the "
                    + Workflow.MAX_FILE_BYTES + " byte cap"));
            return new Result(null, res);
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            res.add(WorkflowIssue.error("not valid JSON: " + e.getMessage()));
            return new Result(null, res);
        }
        if (!root.isJsonObject()) {
            res.add(WorkflowIssue.error("top level must be a JSON object"));
            return new Result(null, res);
        }
        return parseObject(root.getAsJsonObject());
    }

    public static Result parseObject(JsonObject raw) {
        WorkflowValidationResult res = new WorkflowValidationResult();
        // Stage 1: normalise (typo repair + safe coercion), surfacing its warnings.
        List<WorkflowIssue> normWarnings = new ArrayList<>();
        JsonObject root = WorkflowNormalizer.normalise(raw, normWarnings);
        for (WorkflowIssue i : normWarnings) res.add(i);

        rejectUnknown(root, TOP_KEYS, null, null, res);

        Workflow wf = new Workflow();

        // version
        Integer version = readInt(root, "version", null, null, res);
        if (version == null) {
            res.add(WorkflowIssue.error(null, "version",
                    "'version' is required and must be " + Workflow.SUPPORTED_VERSION));
        } else if (version != Workflow.SUPPORTED_VERSION) {
            res.add(WorkflowIssue.error(null, "version",
                    "unsupported workflow version " + version + "; this build supports "
                            + Workflow.SUPPORTED_VERSION));
        } else {
            wf.setVersion(version);
        }

        // goal (required)
        String goal = readString(root, "goal", null, null, res);
        if (goal == null || goal.trim().isEmpty()) {
            res.add(WorkflowIssue.error(null, "goal", "'goal' is required and must be a non-empty string"));
        } else {
            wf.setGoal(goal);
        }

        String name = readString(root, "name", null, null, res);
        if (name != null) wf.setName(name);
        String output = readString(root, "output", null, null, res);
        if (output != null) wf.setOutput(output);

        // entry: string or array of strings
        if (root.has("entry") && !root.get("entry").isJsonNull()) {
            JsonElement e = root.get("entry");
            List<String> entries = new ArrayList<>();
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                entries.add(e.getAsString());
            } else if (e.isJsonArray()) {
                if (e.getAsJsonArray().size() == 0) {
                    res.add(WorkflowIssue.error(null, "entry", "'entry' must name at least one agent"));
                }
                for (JsonElement item : e.getAsJsonArray()) {
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) entries.add(item.getAsString());
                    else res.add(WorkflowIssue.error(null, "entry", "'entry' entries must be strings"));
                }
            } else {
                res.add(WorkflowIssue.error(null, "entry", "'entry' must be a string or an array of strings"));
            }
            wf.setEntry(entries);
        }

        // defaults
        if (root.has("defaults") && !root.get("defaults").isJsonNull()) {
            JsonElement d = root.get("defaults");
            if (!d.isJsonObject()) {
                res.add(WorkflowIssue.error(null, "defaults", "'defaults' must be an object"));
            } else {
                wf.setDefaults(parseDefaults(d.getAsJsonObject(), res));
            }
        }

        // agents (required)
        if (!root.has("agents") || !root.get("agents").isJsonObject()) {
            res.add(WorkflowIssue.error(null, "agents", "'agents' is required and must be an object"));
            return new Result(null, res);
        }
        JsonObject agentsObj = root.getAsJsonObject("agents");
        if (agentsObj.size() == 0) {
            res.add(WorkflowIssue.error(null, "agents", "'agents' must contain at least one agent"));
            return new Result(null, res);
        }
        if (agentsObj.size() > wf.getDefaults().getMaxNodes()) {
            res.add(WorkflowIssue.error(null, "agents", agentsObj.size() + " agents exceeds max_nodes ("
                    + wf.getDefaults().getMaxNodes() + ")"));
            return new Result(null, res);
        }

        for (Map.Entry<String, JsonElement> e : agentsObj.entrySet()) {
            String key = e.getKey();
            if (!KEY.matcher(key).matches()) {
                res.add(WorkflowIssue.error(key, null,
                        "agent key must match ^[a-z][a-z0-9_]{0,63}$ (lowercase, digits, underscore)"));
                continue;
            }
            if (Workflow.RESERVED_KEYS.contains(key)) {
                res.add(WorkflowIssue.error(key, null,
                        "'" + key + "' is a reserved template namespace and cannot be an agent key"));
                continue;
            }
            if (e.getValue() == null || !e.getValue().isJsonObject()) {
                res.add(WorkflowIssue.error(key, null, "agent must be an object"));
                continue;
            }
            WorkflowAgent agent = parseAgent(key, e.getValue().getAsJsonObject(), res);
            if (agent != null) wf.putAgent(key, agent);
        }

        if (res.hasErrors()) return new Result(null, res);
        return new Result(wf, res);
    }

    // ==================== defaults ====================

    private static WorkflowDefaults parseDefaults(JsonObject o, WorkflowValidationResult res) {
        rejectUnknown(o, DEFAULTS_KEYS, null, "defaults", res);
        WorkflowDefaults d = new WorkflowDefaults();

        String model = readString(o, "model", null, "defaults", res);
        if (model != null) {
            if (!MODEL_REF.matcher(model).matches()) {
                res.add(WorkflowIssue.error(null, "defaults.model",
                        "'" + model + "' is not a model reference; use 'providerId/modelId'"));
            } else {
                d.setModel(model);
            }
        }
        Integer mt = readInt(o, "max_turns", null, "defaults", res);
        if (mt != null) {
            if (mt < MIN_MAX_TURNS || mt > MAX_MAX_TURNS) {
                res.add(WorkflowIssue.error(null, "defaults.max_turns",
                        mt + " is out of range [" + MIN_MAX_TURNS + ", " + MAX_MAX_TURNS + "]"));
            } else d.setMaxTurns(mt);
        }
        Integer to = readInt(o, "timeout_ms", null, "defaults", res);
        if (to != null) {
            if (to < MIN_TIMEOUT_MS) {
                res.add(WorkflowIssue.error(null, "defaults.timeout_ms",
                        to + " is below the " + MIN_TIMEOUT_MS + "ms minimum"));
            } else d.setTimeoutMs(to);
        }
        d.setAllowedTools(readStringList(o, "allowed_tools", null, "defaults", res, true));
        d.setDeniedTools(readStringList(o, "denied_tools", null, "defaults", res, true));

        String ap = readString(o, "approval", null, "defaults", res);
        if (ap != null) {
            WorkflowApprovalPolicy p = WorkflowApprovalPolicy.fromJson(ap);
            if (p == null) res.add(WorkflowIssue.error(null, "defaults.approval", invalidEnum(ap, "approval")));
            else d.setApproval(p);
        }
        String oe = readString(o, "on_error", null, "defaults", res);
        if (oe != null) {
            WorkflowErrorPolicy p = WorkflowErrorPolicy.fromJson(oe);
            if (p == null) res.add(WorkflowIssue.error(null, "defaults.on_error", invalidEnum(oe, "on_error")));
            else d.setOnError(p);
        }
        if (o.has("retry")) {
            WorkflowRetry r = parseRetry(o.get("retry"), null, "defaults", res);
            if (r != null) d.setRetry(r);
        }
        Integer mp = readInt(o, "max_parallel", null, "defaults", res);
        if (mp != null) {
            if (mp < WorkflowDefaults.MIN_MAX_PARALLEL || mp > WorkflowDefaults.MAX_MAX_PARALLEL) {
                res.add(WorkflowIssue.error(null, "defaults.max_parallel", mp + " is out of range ["
                        + WorkflowDefaults.MIN_MAX_PARALLEL + ", " + WorkflowDefaults.MAX_MAX_PARALLEL + "]"));
            } else d.setMaxParallel(mp);
        }
        Integer mn = readInt(o, "max_nodes", null, "defaults", res);
        if (mn != null) {
            if (mn < WorkflowDefaults.MIN_MAX_NODES || mn > WorkflowDefaults.MAX_MAX_NODES) {
                res.add(WorkflowIssue.error(null, "defaults.max_nodes", mn + " is out of range ["
                        + WorkflowDefaults.MIN_MAX_NODES + ", " + WorkflowDefaults.MAX_MAX_NODES + "]"));
            } else d.setMaxNodes(mn);
        }
        return d;
    }

    // ==================== agent ====================

    private static WorkflowAgent parseAgent(String key, JsonObject o, WorkflowValidationResult res) {
        rejectUnknown(o, AGENT_KEYS, key, null, res);
        WorkflowAgent.Builder b = WorkflowAgent.builder(key);

        b.description(readString(o, "description", key, null, res));

        String model = readString(o, "model", key, "model", res);
        if (model != null) {
            if (!MODEL_REF.matcher(model).matches()) {
                res.add(WorkflowIssue.error(key, "model",
                        "'" + model + "' is not a model reference; use 'providerId/modelId' "
                                + "(a bare model id is rejected rather than silently falling back to the global default)"));
            } else b.model(model);
        }

        // prompt is required
        if (!o.has("prompt") || o.get("prompt").isJsonNull()) {
            res.add(WorkflowIssue.error(key, "prompt", "'prompt' is required"));
        } else if (!o.get("prompt").isJsonObject()) {
            res.add(WorkflowIssue.error(key, "prompt", "'prompt' must be an object"));
        } else {
            WorkflowPrompt p = parsePrompt(key, o.getAsJsonObject("prompt"), res);
            if (p != null) b.prompt(p);
        }

        b.allowedTools(readStringList(o, "allowed_tools", key, "allowed_tools", res, true));
        b.deniedTools(readStringList(o, "denied_tools", key, "denied_tools", res, true));

        Integer mt = readInt(o, "max_turns", key, "max_turns", res);
        if (mt != null) {
            if (mt < MIN_MAX_TURNS || mt > MAX_MAX_TURNS) {
                res.add(WorkflowIssue.error(key, "max_turns",
                        mt + " is out of range [" + MIN_MAX_TURNS + ", " + MAX_MAX_TURNS + "]"));
            } else b.maxTurns(mt);
        }
        Integer to = readInt(o, "timeout_ms", key, "timeout_ms", res);
        if (to != null) {
            if (to < MIN_TIMEOUT_MS) {
                res.add(WorkflowIssue.error(key, "timeout_ms", to + " is below the " + MIN_TIMEOUT_MS + "ms minimum"));
            } else b.timeoutMs(to);
        }
        if (o.has("retry")) {
            WorkflowRetry r = parseRetry(o.get("retry"), key, "retry", res);
            if (r != null) b.retry(r);
        }
        String oe = readString(o, "on_error", key, "on_error", res);
        if (oe != null) {
            WorkflowErrorPolicy p = WorkflowErrorPolicy.fromJson(oe);
            if (p == null) res.add(WorkflowIssue.error(key, "on_error", invalidEnum(oe, "on_error")));
            else b.onError(p);
        }
        String ap = readString(o, "approval", key, "approval", res);
        if (ap != null) {
            WorkflowApprovalPolicy p = WorkflowApprovalPolicy.fromJson(ap);
            if (p == null) res.add(WorkflowIssue.error(key, "approval", invalidEnum(ap, "approval")));
            else b.approval(p);
        }
        if (o.has("output_schema") && !o.get("output_schema").isJsonNull()) {
            if (!o.get("output_schema").isJsonObject()) {
                res.add(WorkflowIssue.error(key, "output_schema", "'output_schema' must be an object or null"));
            } else {
                b.outputSchema(o.getAsJsonObject("output_schema"));
            }
        }
        b.dependsOn(readStringList(o, "depends_on", key, "depends_on", res, false));

        if (o.has("inputs") && !o.get("inputs").isJsonNull()) {
            if (!o.get("inputs").isJsonObject()) {
                res.add(WorkflowIssue.error(key, "inputs", "'inputs' must be an object"));
            } else {
                Map<String, String> in = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> ie : o.getAsJsonObject("inputs").entrySet()) {
                    JsonElement v = ie.getValue();
                    if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()
                            || v.getAsString().isEmpty()) {
                        res.add(WorkflowIssue.error(key, "inputs." + ie.getKey(),
                                "input values must be non-empty template strings"));
                        continue;
                    }
                    in.put(ie.getKey(), v.getAsString());
                }
                b.inputs(in);
            }
        }

        if (o.has("when") && !o.get("when").isJsonNull()) {
            String when = readString(o, "when", key, "when", res);
            if (when != null) {
                if (!GUARD.matcher(when.trim()).matches()) {
                    res.add(WorkflowIssue.error(key, "when",
                            "'" + when + "' is not a valid guard; v1 supports one comparison: "
                                    + "{{expr}} ==|!=|contains 'literal'"));
                } else b.when(when.trim());
            }
        }
        return b.build();
    }

    private static WorkflowPrompt parsePrompt(String key, JsonObject o, WorkflowValidationResult res) {
        rejectUnknown(o, PROMPT_KEYS, key, "prompt", res);
        String system = readString(o, "system", key, "prompt.system", res);
        List<String> forms = new ArrayList<>();
        if (o.has("text")) forms.add("text");
        if (o.has("template")) forms.add("template");
        if (o.has("from_agent")) forms.add("from_agent");

        if (forms.size() != 1) {
            res.add(WorkflowIssue.error(key, "prompt",
                    forms.isEmpty()
                            ? "'prompt' must contain exactly one of 'text', 'template' or 'from_agent'"
                            : "'prompt' must contain exactly one of 'text', 'template' or 'from_agent', but found "
                              + forms));
            return null;
        }
        String form = forms.get(0);
        JsonElement v = o.get(form);
        if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
            res.add(WorkflowIssue.error(key, "prompt." + form, "'prompt." + form + "' must be a string"));
            return null;
        }
        String s = v.getAsString();
        if (s.trim().isEmpty()) {
            res.add(WorkflowIssue.error(key, "prompt." + form, "'prompt." + form + "' must not be empty"));
            return null;
        }
        if ("from_agent".equals(form)) {
            if (!KEY.matcher(s).matches()) {
                res.add(WorkflowIssue.error(key, "prompt.from_agent",
                        "'" + s + "' is not a valid agent key"));
                return null;
            }
            return WorkflowPrompt.fromAgent(system, s);
        }
        if (TemplateResolver.hasUnbalancedBraces(s)) {
            res.add(WorkflowIssue.error(key, "prompt." + form,
                    "unbalanced {{ }} in the template"));
            return null;
        }
        return "text".equals(form)
                ? WorkflowPrompt.text(system, s)
                : WorkflowPrompt.template(system, s);
    }

    private static WorkflowRetry parseRetry(JsonElement el, String key, String field, WorkflowValidationResult res) {
        if (el.isJsonNull()) return null;
        if (!el.isJsonObject()) {
            res.add(WorkflowIssue.error(key, field, "'" + field + "' must be an object"));
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        rejectUnknown(o, RETRY_KEYS, key, field, res);
        int attempts = 1, backoff = 0;
        Integer a = readInt(o, "max_attempts", key, field + ".max_attempts", res);
        if (a != null) {
            if (!WorkflowRetry.isValidAttempts(a)) {
                res.add(WorkflowIssue.error(key, field + ".max_attempts", a + " is out of range ["
                        + WorkflowRetry.MIN_ATTEMPTS + ", " + WorkflowRetry.MAX_ATTEMPTS + "]"));
            } else attempts = a;
        }
        Integer bk = readInt(o, "backoff_ms", key, field + ".backoff_ms", res);
        if (bk != null) {
            if (!WorkflowRetry.isValidBackoff(bk)) {
                res.add(WorkflowIssue.error(key, field + ".backoff_ms", bk + " is out of range [0, "
                        + WorkflowRetry.MAX_BACKOFF_MS + "]"));
            } else backoff = bk;
        }
        return new WorkflowRetry(attempts, backoff);
    }

    // ==================== strict readers ====================

    private static void rejectUnknown(JsonObject o, Set<String> allowed, String key, String scope,
                                      WorkflowValidationResult res) {
        for (String member : o.keySet()) {
            if (allowed.contains(member)) continue;
            String where = scope != null ? scope : "workflow";
            res.add(WorkflowIssue.error(key, member,
                    "unknown property '" + member + "' in " + where
                            + "; allowed: " + sorted(allowed)));
        }
    }

    private static String readString(JsonObject o, String name, String key, String field, WorkflowValidationResult res) {
        if (!o.has(name) || o.get(name).isJsonNull()) return null;
        JsonElement v = o.get(name);
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) {
            res.add(WorkflowIssue.error(key, field == null ? name : field, "'" + name + "' must be a string"));
            return null;
        }
        return v.getAsString();
    }

    private static Integer readInt(JsonObject o, String name, String key, String field, WorkflowValidationResult res) {
        if (!o.has(name) || o.get(name).isJsonNull()) return null;
        JsonElement v = o.get(name);
        String label = field == null ? name : field;
        if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) {
            res.add(WorkflowIssue.error(key, label, "'" + name + "' must be an integer"));
            return null;
        }
        double d = v.getAsDouble();
        if (d != Math.floor(d) || Double.isInfinite(d)) {
            res.add(WorkflowIssue.error(key, label, "'" + name + "' must be an integer, got " + d));
            return null;
        }
        return (int) d;
    }

    /**
     * Reads a string array. Returns {@code null} when the key is absent, and an
     * empty list when it is present but empty — a distinction that matters for
     * {@code allowed_tools} (§9).
     */
    private static List<String> readStringList(JsonObject o, String name, String key, String field,
                                               WorkflowValidationResult res, boolean checkToolShape) {
        if (!o.has(name) || o.get(name).isJsonNull()) return null;
        JsonElement v = o.get(name);
        String label = field == null ? name : field;
        if (!v.isJsonArray()) {
            res.add(WorkflowIssue.error(key, label, "'" + name + "' must be an array of strings"));
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : v.getAsJsonArray()) {
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                res.add(WorkflowIssue.error(key, label, "'" + name + "' must contain only strings"));
                return null;
            }
            String s = item.getAsString();
            if (checkToolShape && !TOOL_NAME.matcher(s).matches()) {
                res.add(WorkflowIssue.error(key, label, "'" + s + "' is not a valid tool name"));
                continue;
            }
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    private static String invalidEnum(String value, String field) {
        if ("approval".equals(field)) {
            return "'" + value + "' is not a valid approval policy (use inherit, auto_approve, deny_writes, strict)";
        }
        return "'" + value + "' is not a valid on_error policy (use fail, skip, continue)";
    }

    private static String sorted(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        java.util.Collections.sort(l);
        return String.join(", ", l);
    }
}
