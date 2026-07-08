import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyFilterTest {

    /** The example from the task description. */
    @Test
    void testFilter() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId % 3 != 0);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 2, 5, 8, 10, 11},
                new int[]{0, 1, 1, 0, 1, 2}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    /**
     * A node that fails removes its whole subtree, even children that would pass on their own.
     * Node 5 (depth 1) fails; its child 2 passes the predicate but must still be removed.
     * The later sibling 3 must survive, proving pruning stops at the right place.
     */
    @Test
    void testMidNodeFailsRemovesPassingChild() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 5, 2, 3},
                new int[]{0, 1, 2, 1}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId <= 3);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 3},
                new int[]{0, 1}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    /** An empty hierarchy filters to an empty hierarchy. */
    @Test
    void testEmptyHierarchy() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(new int[]{}, new int[]{});
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> true);
        assertEquals("[]", filteredActual.formatString());
    }

    /** A predicate that accepts everything returns an identical hierarchy. */
    @Test
    void testKeepEverything() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> true);
        assertEquals(unfiltered.formatString(), filteredActual.formatString());
    }

    /** A predicate that rejects everything returns an empty hierarchy. */
    @Test
    void testKeepNothing() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> false);
        assertEquals("[]", filteredActual.formatString());
    }

    /**
     * A failing root removes its entire tree but must not affect the following independent trees.
     * Root 6 fails, so 6 and 7 disappear; roots 1 and 8 (and their subtrees) are untouched.
     */
    @Test
    void testFailingRootRemovesOnlyItsOwnTree() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 6, 7, 8, 9},
                new int[]{0, 1, 0, 1, 0, 1}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId != 6);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 2, 8, 9},
                new int[]{0, 1, 0, 1}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    /** A failing leaf removes only itself. */
    @Test
    void testFailingLeafRemovesOnlyItself() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3},
                new int[]{0, 1, 1}
        );
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId != 2);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 3},
                new int[]{0, 1}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }

    /** A single passing node survives. */
    @Test
    void testSingleNodePasses() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(new int[]{1}, new int[]{0});
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> true);
        assertEquals("[1:0]", filteredActual.formatString());
    }

    /** A single failing node is removed. */
    @Test
    void testSingleNodeFails() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(new int[]{1}, new int[]{0});
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> false);
        assertEquals("[]", filteredActual.formatString());
    }

    /**
     * A very deep chain (100k levels) must be handled without a StackOverflowError.
     * This guards against a recursive implementation: the algorithm is iterative, so an
     * arbitrarily deep hierarchy is processed in constant stack space.
     */
    @Test
    void testDeepHierarchyDoesNotOverflowStack() {
        int n = 100_000;
        int[] nodeIds = new int[n];
        int[] depths = new int[n];
        for (int i = 0; i < n; i++) {
            nodeIds[i] = i;
            depths[i] = i; // each node is the single child of the previous one
        }
        Hierarchy unfiltered = new ArrayBasedHierarchy(nodeIds, depths);

        // Keeps every node: exercises the full-depth traversal without pruning.
        Hierarchy filtered = HierarchyFilter.filter(unfiltered, nodeId -> true);

        assertEquals(n, filtered.size());
        assertEquals(0, filtered.nodeId(0));
        assertEquals(n - 1, filtered.nodeId(n - 1));
        assertEquals(n - 1, filtered.depth(n - 1));
    }

    /**
     * The predicate must be evaluated at most once per node, and not at all for nodes that are
     * pruned because an ancestor failed. This documents the efficiency contract of the algorithm.
     *
     * <p>For the sample forest with predicate {@code nodeId % 3 != 0}, nodes 3, 6 and 9 fail.
     * Nodes 4 and 7 are descendants of failed nodes (3 and 6) and must never be tested, so the
     * predicate is invoked on 9 of the 11 nodes.
     */
    @Test
    void testPredicateEvaluatedAtMostOncePerNodeAndNeverForPrunedNodes() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11},
                new int[]{0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2}
        );

        int[] callsPerNodeId = new int[12]; // index == nodeId (ids are 1..11)
        IntPredicate counting = nodeId -> {
            callsPerNodeId[nodeId]++;
            return nodeId % 3 != 0;
        };

        HierarchyFilter.filter(unfiltered, counting);

        // No node is evaluated more than once.
        for (int id = 1; id <= 11; id++) {
            assertTrue(callsPerNodeId[id] <= 1, "node " + id + " was evaluated more than once");
        }
        // Descendants of failed ancestors are never evaluated.
        assertEquals(0, callsPerNodeId[4], "node 4 (child of failed 3) must not be evaluated");
        assertEquals(0, callsPerNodeId[7], "node 7 (child of failed 6) must not be evaluated");
        // 11 nodes minus the 2 pruned descendants = 9 predicate calls in total.
        assertEquals(9, Arrays.stream(callsPerNodeId).sum());
    }

    /** Deep chain where an intermediate node fails removes everything below it. */
    @Test
    void testDeepChainPrunedFromMiddle() {
        Hierarchy unfiltered = new ArrayBasedHierarchy(
                new int[]{1, 2, 3, 4, 5},
                new int[]{0, 1, 2, 3, 4}
        );
        // 3 fails, so 4 and 5 (its descendants) are removed; 1 and 2 remain.
        Hierarchy filteredActual = HierarchyFilter.filter(unfiltered, nodeId -> nodeId != 3);
        Hierarchy filteredExpected = new ArrayBasedHierarchy(
                new int[]{1, 2},
                new int[]{0, 1}
        );
        assertEquals(filteredExpected.formatString(), filteredActual.formatString());
    }
}
