import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * A {@code Hierarchy} stores an arbitrary <i>forest</i> (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * <p>Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * <p>Example:
 * <pre>
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * </pre>
 *
 * <p>the forest can be visualized as follows:
 * <pre>
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 * </pre>
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * <p>Invariants on the depths array:
 * <ul>
 *   <li>Depth of the first element is 0.</li>
 *   <li>If the depth of a node is {@code D}, the depth of the next node in the array can be:
 *     <ul>
 *       <li>{@code D + 1} if the next node is a child of this node;</li>
 *       <li>{@code D} if the next node is a sibling of this node;</li>
 *       <li>{@code d < D} - in this case the next node is not related to this node.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
interface Hierarchy {
    /** The number of nodes in the hierarchy. */
    int size();

    /**
     * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be {@code depth(index)}.
     * @param index must be non-negative and less than {@link #size()}
     */
    int nodeId(int index);

    /**
     * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be {@code nodeId(index)}.
     * @param index must be non-negative and less than {@link #size()}
     */
    int depth(int index);

    default String formatString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nodeId(i)).append(":").append(depth(i));
        }
        sb.append("]");
        return sb.toString();
    }
}

/**
 * Filters a {@link Hierarchy}: a node is present in the filtered hierarchy iff its node ID passes
 * the predicate and all of its ancestors pass it as well.
 *
 * <p>Assumptions:
 * <ul>
 *   <li>A new {@link Hierarchy} is returned; the input is never mutated.</li>
 *   <li>The input satisfies the depth invariants documented on {@link Hierarchy} (DFS order,
 *       first node at depth 0, depth increases by at most 1 between consecutive nodes).</li>
 *   <li>Node IDs are unique, as stated by {@link Hierarchy#nodeId(int)}.</li>
 *   <li>Surviving nodes keep their original depth: a node only survives if all of its ancestors
 *       survive, so its ancestor chain is intact and its depth is unchanged.</li>
 *   <li>{@code hierarchy} and {@code nodeIdPredicate} must be non-null (enforced, throws {@link NullPointerException}).</li>
 * </ul>
 */
class HierarchyFilter {

    /**
     * Filters the hierarchy in a single forward pass.
     *
     * <p>Because nodes are in DFS order, the descendants of any node form a contiguous run that
     * immediately follows it, all at a strictly greater depth. When a node fails the predicate we
     * remember the depth at which its (dead) subtree started in {@code prunedDepth}; every following
     * node deeper than that is part of the dead subtree and is skipped, until we reach a node at that
     * depth or shallower, at which point we have left the dead subtree and resume normal evaluation.
     *
     * <p>Runs in O(n) time and O(1) additional state beyond the output arrays.
     */
    public static Hierarchy filter(Hierarchy hierarchy, IntPredicate nodeIdPredicate) {
        Objects.requireNonNull(hierarchy, "hierarchy must not be null");
        Objects.requireNonNull(nodeIdPredicate, "nodeIdPredicate must not be null");

        // Upper bound on the result size is the input size (we can only remove nodes, never add).
        int[] nodeIds = new int[hierarchy.size()];
        int[] depths = new int[hierarchy.size()];
        int count = 0;

        // Depth at which the subtree we are currently pruning began; MAX_VALUE means "not pruning".
        int prunedDepth = Integer.MAX_VALUE;

        for (int i = 0; i < hierarchy.size(); i++) {
            int d = hierarchy.depth(i);

            // A node at this depth or shallower means we have left any pruned subtree.
            if (d <= prunedDepth) {
                prunedDepth = Integer.MAX_VALUE;
            }

            // Still inside a pruned (failed ancestor's) subtree: this node is removed regardless.
            if (prunedDepth != Integer.MAX_VALUE) {
                continue;
            }

            int id = hierarchy.nodeId(i);
            if (nodeIdPredicate.test(id)) {
                // Node passes and all ancestors passed: keep it with its original depth.
                nodeIds[count] = id;
                depths[count] = d;
                count++;
            } else {
                // Node fails: start pruning its subtree.
                prunedDepth = d;
            }
        }

        return new ArrayBasedHierarchy(Arrays.copyOf(nodeIds, count), Arrays.copyOf(depths, count));
    }
}

class ArrayBasedHierarchy implements Hierarchy {
    private final int[] nodeIds;
    private final int[] depths;

    public ArrayBasedHierarchy(int[] nodeIds, int[] depths) {
        Objects.requireNonNull(nodeIds, "nodeIds must not be null");
        Objects.requireNonNull(depths, "depths must not be null");
        if (nodeIds.length != depths.length) {
            throw new IllegalArgumentException(
                    "nodeIds and depths must have the same length: " + nodeIds.length + " != " + depths.length);
        }
        // Defensive copies so the hierarchy is immutable and cannot be changed by the caller after construction.
        this.nodeIds = Arrays.copyOf(nodeIds, nodeIds.length);
        this.depths = Arrays.copyOf(depths, depths.length);
    }

    @Override
    public int size() {
        return depths.length;
    }

    @Override
    public int nodeId(int index) {
        return nodeIds[index];
    }

    @Override
    public int depth(int index) {
        return depths[index];
    }
}
