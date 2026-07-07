# SimpleCache — Code Review

## Code under review

```java
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>();
    private final long ttlMs = 60000; // 1 minute

    public static class CacheEntry<V> {
        private final V value;
        private final long timestamp;

        public CacheEntry(V value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public V getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis()));
    }

    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() - entry.getTimestamp() < ttlMs) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int size() {
        return cache.size();
    }
}
```

The map itself is thread-safe (`ConcurrentHashMap`), so this will not corrupt or crash under concurrent access. The problems below are about behaviour **over time and under load** in the stated environment (thousands of reads/sec, hundreds of writes/sec, tens of threads).

## Review

1. **Unbounded memory growth (memory leak).**
   When an entry is expired, `get()` returns `null` but never removes it from the map, and there is no size cap and no cleanup process. Entries that are written once and never read again are never revisited, so they stay in memory forever. At hundreds of new keys/sec that is millions of dead entries per day, growing without bound until the JVM dies with `OutOfMemoryError`; well before that, the growing heap causes longer and more frequent GC pauses that degrade latency.
   Removing an entry on read is not sufficient by itself, because it only reclaims keys that happen to be read again after expiring. A correct fix needs **active/background expiration** *and* a **bounded maximum size with an eviction policy (e.g. LRU)**. In practice I would use a proven library such as **Caffeine**, which provides TTL expiry, size bounds, and background maintenance out of the box.

2. **Hardcoded TTL.**
   The 60-second TTL is a hardcoded `final` field, so this generic, reusable cache forces one expiry policy on every caller and cannot be tuned per use case or per environment without editing the source. It should be **passed in via the constructor**, ideally as a `java.time.Duration` (self-documenting units, no magic `60000` literal). Allowing a per-entry TTL would make it more flexible still.

3. **Wall-clock time used to measure elapsed time.**
   TTL is measured with `System.currentTimeMillis()`, which is wall-clock time and can jump **backwards** (NTP correction, manual clock changes, etc.). When that happens, `now - timestamp` becomes unreliable and entries can expire too early or too late. Elapsed-time checks should use the monotonic clock **`System.nanoTime()`** (stamped and compared consistently on entries). Injecting a `Clock` would be even better, as it also makes expiry unit-testable without `Thread.sleep`.

4. **No protection against cache stampede (thundering herd).**
   This is a read-heavy cache. When a popular key expires, many concurrent readers all miss at the same moment, and — because loading on a miss is left to the caller (`get` → `null` → load from DB → `put`) — they each independently hit the backing store. For a hot key that can mean hundreds of identical DB queries when only **one** load was needed, overloading the backend exactly when it is busiest and potentially cascading into an outage. The cache should own loading and **single-flight** it per key, e.g. expose a `get(key, loader)` / loading-cache method backed by `ConcurrentHashMap.computeIfAbsent` (or Caffeine's loading cache), so only one thread computes a missing value while the others wait for its result.

5. **`size()` is misleading.**
   `size()` counts expired-but-not-yet-evicted entries, so it does not reflect the number of *live* values a caller can actually get; it is also only weakly consistent under concurrent modification. Callers may misinterpret it (e.g. for capacity decisions).

6. **No observability / metrics.**
   A production cache should expose hit/miss ratios (and ideally eviction/load counts). Without them there is no way to tune the TTL or the size, or to tell whether the cache is actually helping.

7. **Ambiguous `null` contract.**
   `get()` returns `null` for both "absent" and "expired", and `ConcurrentHashMap` cannot store `null` values anyway, so there is no way for the cache to represent a legitimately cached `null`. The API should document this (or use `Optional`) so callers are not surprised.
