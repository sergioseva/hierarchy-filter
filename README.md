# Take-Home Assessment (Java 21)

This repository contains solutions to both tasks.

## Task 1 — Hierarchy Filter

`HierarchyFilter.filter()` removes every node that fails the predicate **and** every node
that has an ancestor which fails the predicate, preserving DFS order.

- Implementation: [`src/main/java/Hierarchy.java`](src/main/java/Hierarchy.java)
- Tests: [`src/test/java/HierarchyFilterTest.java`](src/test/java/HierarchyFilterTest.java)

### Approach

The forest is stored in DFS order, so the descendants of any node are a contiguous run that
immediately follows it, all at a strictly greater depth. The filter is therefore a single
forward pass that tracks one integer, `prunedDepth` — the depth at which the subtree currently
being pruned started:

- when a node **fails**, we start pruning at its depth;
- any following node **deeper** than that is part of the failed subtree and is skipped;
- the first node at that depth or shallower means we have left the failed subtree, so we
  resume normal evaluation.

This runs in **O(n)** time with **O(1)** extra state (beyond the output), and is iterative,
so it is safe for very deep hierarchies (no recursion / stack-overflow risk). Surviving nodes
keep their original depth, because a node only survives when its whole ancestor chain survives.

### Assumptions

- `filter()` returns a **new** `Hierarchy`; the input is never mutated.
- The input satisfies the documented depth invariants (DFS order, first node at depth 0,
  depth increases by at most 1 between consecutive nodes).
- Node IDs are unique, as stated by `Hierarchy.nodeId(int)`.
- `hierarchy` and `nodeIdPredicate` are non-null.

## Task 2 — SimpleCache Code Review

The review is in [`SimpleCache.md`](SimpleCache.md) as a numbered list, each item with its
impact under the stated high-concurrency environment.

## Building and running the tests

Requires JDK 21 (the Gradle wrapper handles Gradle itself):

```bash
./gradlew test
```
