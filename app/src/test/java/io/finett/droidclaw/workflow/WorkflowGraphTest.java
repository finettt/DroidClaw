package io.finett.droidclaw.workflow;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Covers §8.2 edge derivation and §10.2 scheduling primitives. */
public class WorkflowGraphTest {

    /**
     * Parses stages 1-2 only. Graph tests deliberately use fixtures that stage 3
     * must reject (cycles, unknown references), so full loading would return null.
     */
    private static Workflow load(String json) {
        WorkflowParser.Result r = WorkflowParser.parse(json);
        assertEquals("test fixture must parse: " + r.getIssues().describe(),
                0, r.getIssues().getErrors().size());
        assertNotNull(r.getWorkflow());
        return r.getWorkflow();
    }

    @Test
    public void templateReferenceIsAnEdge() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"triage\":{\"prompt\":{\"text\":\"start\"}},"
                + "\"scan\":{\"prompt\":{\"template\":\"area {{triage.output}}\"}}}}");
        WorkflowGraph g = WorkflowGraph.build(wf);
        assertTrue("bare {{triage.output}} must order triage before scan",
                g.dependenciesOf("scan").contains("triage"));
        assertTrue(g.dependentsOf("triage").contains("scan"));
        assertEquals(Collections.singleton("triage"), g.roots());
        assertEquals(Arrays.asList("triage", "scan"), g.topologicalOrder());
    }

    @Test
    public void fromAgentDesugarsToAnEdge() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"from_agent\":\"a\"}}}}");
        assertTrue(WorkflowGraph.build(wf).dependenciesOf("b").contains("a"));
    }

    @Test
    public void inputsAndDependsOnAndGuardAllCreateEdges() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"text\":\"y\"}},"
                + "\"c\":{\"prompt\":{\"text\":\"z\"}},"
                + "\"d\":{\"prompt\":{\"template\":\"use {{inputs.x}}\"},"
                +     "\"inputs\":{\"x\":\"{{a.output}}\"},\"depends_on\":[\"b\"],"
                +     "\"when\":\"{{c.output}} == 'go'\"}}}");
        Set<String> deps = WorkflowGraph.build(wf).dependenciesOf("d");
        assertEquals(new HashSet<>(Arrays.asList("a", "b", "c")), deps);
    }

    @Test
    public void fanInHasParallelBranches() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"root\":{\"prompt\":{\"text\":\"r\"}},"
                + "\"l\":{\"prompt\":{\"template\":\"{{root.output}}\"}},"
                + "\"r\":{\"prompt\":{\"template\":\"{{root.output}}\"}},"
                + "\"join\":{\"prompt\":{\"template\":\"{{l.output}} {{r.output}}\"}}}}");
        WorkflowGraph g = WorkflowGraph.build(wf);
        assertEquals("join reads only l and r", new HashSet<>(Arrays.asList("l", "r")),
                g.dependenciesOf("join"));
        assertTrue("l and r must be independent so they can run in parallel",
                !g.dependenciesOf("l").contains("r") && !g.dependenciesOf("r").contains("l"));
        assertEquals(Collections.singleton("root"), g.roots());
        assertEquals(Collections.singleton("join"), g.leaves());
    }

    @Test
    public void detectsTwoNodeCycle() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"template\":\"{{b.output}}\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}}}}");
        List<String> cyc = WorkflowGraph.build(wf).detectCycle();
        assertNotNull("cycle must be detected", cyc);
        assertEquals("cycle path must close on itself", cyc.get(0), cyc.get(cyc.size() - 1));
        assertTrue(cyc.contains("a") && cyc.contains("b"));
    }

    @Test
    public void detectsThreeNodeCycle() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"template\":\"{{c.output}}\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}},"
                + "\"c\":{\"prompt\":{\"template\":\"{{b.output}}\"}}}}");
        WorkflowGraph g = WorkflowGraph.build(wf);
        assertNotNull(g.detectCycle());
        assertNull("topological order must be null for a cyclic graph", g.topologicalOrder());
    }

    @Test
    public void acyclicGraphHasNoCycle() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}}}}");
        assertNull(WorkflowGraph.build(wf).detectCycle());
    }

    @Test
    public void transitiveDependents() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"template\":\"{{a.output}}\"}},"
                + "\"c\":{\"prompt\":{\"template\":\"{{b.output}}\"}},"
                + "\"d\":{\"prompt\":{\"template\":\"{{a.output}}\"}}}}");
        Set<String> t = WorkflowGraph.build(wf).transitiveDependents("a");
        assertEquals(new HashSet<>(Arrays.asList("b", "c", "d")), t);
        assertEquals(Collections.singleton("c"), WorkflowGraph.build(wf).transitiveDependents("b"));
        assertTrue(WorkflowGraph.build(wf).transitiveDependents("c").isEmpty());
    }

    @Test
    public void readySetGrowsAsNodesComplete() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"text\":\"x\"}},"
                + "\"b\":{\"prompt\":{\"text\":\"y\"}},"
                + "\"c\":{\"prompt\":{\"template\":\"{{a.output}}{{b.output}}\"}}}}");
        WorkflowGraph g = WorkflowGraph.build(wf);
        Set<String> done = new HashSet<>(), running = new HashSet<>();
        assertEquals(Arrays.asList("a", "b"), g.readySet(done, running));

        running.add("a");
        assertEquals(Collections.singletonList("b"), g.readySet(done, running));

        done.add("a");
        running.clear();
        assertEquals(Collections.singletonList("b"), g.readySet(done, running));

        done.add("b");
        assertEquals(Collections.singletonList("c"), g.readySet(done, running));

        done.add("c");
        assertTrue(g.readySet(done, running).isEmpty());
    }

    @Test
    public void unknownReferencesAreIgnoredByTheGraphNotThrown() {
        Workflow wf = load("{\"version\":1,\"goal\":\"g\",\"agents\":{"
                + "\"a\":{\"prompt\":{\"template\":\"{{ghost.output}}\"}}}}");
        WorkflowGraph g = WorkflowGraph.build(wf);
        assertTrue("unknown refs are the validator's job, not the graph's",
                g.dependenciesOf("a").isEmpty());
    }
}
