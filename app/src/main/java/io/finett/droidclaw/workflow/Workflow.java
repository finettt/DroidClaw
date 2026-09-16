package io.finett.droidclaw.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed workflow: several subagents, their configuration, and how their
 * outputs connect, working toward one {@link #getGoal() goal}.
 *
 * <p>Agent order is preserved from the source file (insertion order) so that
 * deterministic scheduling and readable error messages are possible.
 */
public final class Workflow {

    public static final int SUPPORTED_VERSION = 1;
    public static final int MAX_FILE_BYTES = 64 * 1024;

    /** Namespace roots that may not be used as agent keys. */
    public static final Set<String> RESERVED_KEYS = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.Arrays.asList("workflow", "input", "goal", "output", "env")));

    private int version = SUPPORTED_VERSION;
    private String name;
    private String goal;
    private final List<String> entry = new ArrayList<>();
    private String output;
    private WorkflowDefaults defaults = new WorkflowDefaults();
    private final Map<String, WorkflowAgent> agents = new LinkedHashMap<>();

    public Workflow() {}

    public int getVersion() { return version; }
    public void setVersion(int v) { this.version = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getGoal() { return goal; }
    public void setGoal(String v) { this.goal = v; }
    public String getOutput() { return output; }
    public void setOutput(String v) { this.output = v; }
    public WorkflowDefaults getDefaults() { return defaults; }
    public void setDefaults(WorkflowDefaults v) { this.defaults = v == null ? new WorkflowDefaults() : v; }

    /** Entry agent keys; empty means "every node with no inbound edges". */
    public List<String> getEntry() { return Collections.unmodifiableList(entry); }
    public void setEntry(List<String> v) {
        entry.clear();
        if (v != null) entry.addAll(v);
    }

    public Map<String, WorkflowAgent> getAgents() { return Collections.unmodifiableMap(agents); }

    public void putAgent(String key, WorkflowAgent agent) { agents.put(key, agent); }

    public WorkflowAgent getAgent(String key) { return agents.get(key); }

    public boolean hasAgent(String key) { return agents.containsKey(key); }

    public Set<String> getAgentKeys() { return Collections.unmodifiableSet(agents.keySet()); }

    public int getAgentCount() { return agents.size(); }
}
