package io.finett.droidclaw.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The workflow dependency DAG, built as the <b>union</b> of edges from four
 * sources: {@code depends_on}, {@code inputs}, {@code from_agent}, and every
 * {@code {{<agent>...}}} template reference (spec §8.2).
 *
 * <p>Including template references is mandatory, not an optimisation: without it
 * a node that reads {@code {{triage.output.area}}} could be scheduled before
 * {@code triage} has run.
 *
 * <p>References to undeclared agents are recorded as edges to unknown keys and
 * left for {@link WorkflowValidator} to report; the graph itself never throws.
 */
public final class WorkflowGraph {

    /** agent key -> keys it depends on (upstream). Insertion-ordered for determinism. */
    private final Map<String, Set<String>> upstream = new LinkedHashMap<>();
    /** agent key -> keys that depend on it (downstream). */
    private final Map<String, Set<String>> downstream = new LinkedHashMap<>();
    private final Set<String> declared;

    private WorkflowGraph(Set<String> declared) {
        this.declared = declared;
        for (String k : declared) {
            upstream.put(k, new LinkedHashSet<>());
            downstream.put(k, new LinkedHashSet<>());
        }
    }

    public static WorkflowGraph build(Workflow wf) {
        WorkflowGraph g = new WorkflowGraph(new LinkedHashSet<>(wf.getAgentKeys()));
        for (Map.Entry<String, WorkflowAgent> e : wf.getAgents().entrySet()) {
            String key = e.getKey();
            WorkflowAgent a = e.getValue();

            for (String d : a.getDependsOn()) g.addEdge(key, d);
            if (a.getPrompt() != null) {
                if (a.getPrompt().getFromAgent() != null) g.addEdge(key, a.getPrompt().getFromAgent());
                // effectiveTemplate() desugars from_agent, so this also covers TEXT/TEMPLATE
                for (String ref : TemplateResolver.referencedAgents(a.getPrompt().effectiveTemplate())) {
                    g.addEdge(key, ref);
                }
                for (String ref : TemplateResolver.referencedAgents(a.getPrompt().getSystem())) {
                    g.addEdge(key, ref);
                }
            }
            for (String expr : a.getInputs().values()) {
                for (String ref : TemplateResolver.referencedAgents(expr)) g.addEdge(key, ref);
            }
            if (a.hasGuard()) {
                for (String ref : TemplateResolver.referencedAgents(a.getWhen())) g.addEdge(key, ref);
            }
        }
        return g;
    }

    /** Adds an edge {@code from -> depends on -> to}, ignoring self-edges (reported by the validator). */
    private void addEdge(String from, String to) {
        if (to == null || !declared.contains(to)) return;   // unknown refs: validator's job
        if (from.equals(to)) return;                        // self-dep: validator's job
        upstream.get(from).add(to);
        downstream.get(to).add(from);
    }

    public Set<String> getDeclared() { return Collections.unmodifiableSet(declared); }

    public Set<String> dependenciesOf(String key) {
        Set<String> s = upstream.get(key);
        return s == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(s);
    }

    public Set<String> dependentsOf(String key) {
        Set<String> s = downstream.get(key);
        return s == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(s);
    }

    /** Nodes with no inbound edges. These run first when {@code entry} is omitted. */
    public Set<String> roots() {
        Set<String> out = new LinkedHashSet<>();
        for (String k : declared) if (upstream.get(k).isEmpty()) out.add(k);
        return out;
    }

    public Set<String> leaves() {
        Set<String> out = new LinkedHashSet<>();
        for (String k : declared) if (downstream.get(k).isEmpty()) out.add(k);
        return out;
    }

    /** Every node reachable downstream from {@code key}, excluding {@code key} itself. */
    public Set<String> transitiveDependents(String key) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(key);
        while (!q.isEmpty()) {
            for (String d : downstream.getOrDefault(q.poll(), Collections.emptySet())) {
                if (seen.add(d)) q.add(d);
            }
        }
        seen.remove(key);
        return seen;
    }

    /**
     * Finds one cycle, returned as a path ending where it began
     * (e.g. {@code [a, b, a]}), or {@code null} when the graph is acyclic.
     */
    public List<String> detectCycle() {
        Map<String, Integer> color = new HashMap<>();
        for (String k : declared) color.put(k, 0);
        Deque<String> stack = new ArrayDeque<>();
        for (String k : declared) {
            if (color.get(k) == 0) {
                List<String> cyc = dfs(k, color, stack);
                if (cyc != null) return cyc;
            }
        }
        return null;
    }

    private List<String> dfs(String n, Map<String, Integer> color, Deque<String> stack) {
        color.put(n, 1);
        stack.addLast(n);
        for (String m : upstream.get(n)) {
            Integer c = color.get(m);
            if (c != null && c == 1) {
                List<String> path = new ArrayList<>(stack);
                int i = path.indexOf(m);
                List<String> cyc = new ArrayList<>(path.subList(i, path.size()));
                cyc.add(m);
                return cyc;
            }
            if (c != null && c == 0) {
                List<String> cyc = dfs(m, color, stack);
                if (cyc != null) return cyc;
            }
        }
        stack.removeLast();
        color.put(n, 2);
        return null;
    }

    /**
     * Deterministic topological order (dependencies before dependents), using the
     * file's declaration order to break ties.
     *
     * @return the order, or {@code null} when the graph has a cycle.
     */
    public List<String> topologicalOrder() {
        Map<String, Integer> remaining = new HashMap<>();
        for (String k : declared) remaining.put(k, upstream.get(k).size());
        List<String> order = new ArrayList<>(declared.size());
        Set<String> done = new HashSet<>();
        boolean progress = true;
        while (order.size() < declared.size() && progress) {
            progress = false;
            for (String k : declared) {           // declaration order => deterministic
                if (!done.contains(k) && remaining.get(k) == 0) {
                    order.add(k);
                    done.add(k);
                    for (String d : downstream.get(k)) remaining.put(d, remaining.get(d) - 1);
                    progress = true;
                }
            }
        }
        return order.size() == declared.size() ? order : null;
    }

    /** Nodes runnable once {@code completed} have finished (terminal in any state). */
    public List<String> readySet(Set<String> completed, Set<String> running) {
        List<String> out = new ArrayList<>();
        for (String k : declared) {
            if (completed.contains(k) || running.contains(k)) continue;
            if (completed.containsAll(upstream.get(k))) out.add(k);
        }
        return out;
    }
}
