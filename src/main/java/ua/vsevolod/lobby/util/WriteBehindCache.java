package ua.vsevolod.lobby.util;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Write-behind cache: in-memory state with debounced async persistence.
 *
 * <h3>Per-key debounce — by design</h3>
 * Each key has its OWN independent timer. A {@link #put} for key {@code A} schedules / refreshes
 * key {@code A}'s timer only; it does not touch key {@code B}'s pending write. Two players toggling
 * preferences in parallel produce two independent virtual-thread writes after their own debounce
 * windows expire — one player's spam never starves the other's persistence.
 *
 * <h3>Why</h3>
 * Writing to disk / MongoDB on every state change wastes I/O — and lets a misbehaving client
 * spam writes by toggling rapidly. This cache coalesces changes: any number of {@link #put}
 * calls for the same key within {@code debounce} of each other result in a single async write,
 * scheduled {@code debounce} after the LAST change for THAT key.
 *
 * <h3>Threading model — entirely off the tick thread</h3>
 * <ul>
 *   <li><b>Scheduling</b> uses a single shared daemon thread ({@link BackgroundScheduler#SHARED}),
 *       not the Minestom scheduler. The tick loop never sees these timers.</li>
 *   <li><b>The actual write</b> ({@code writer.accept}) runs on a fresh virtual thread, so
 *       file / Mongo latency can't backpressure either the scheduler or any other path.</li>
 *   <li><b>Per-key swaps</b> ({@link #scheduleFlush}) are done via {@link Map#compute} so the
 *       cancel-old + schedule-new pair is atomic relative to concurrent {@code put} calls on
 *       the SAME key — no orphan task can leak.</li>
 *   <li><b>Reads</b> are lock-free ({@link ConcurrentHashMap}).</li>
 * </ul>
 *
 * <h3>Semantics</h3>
 * <ul>
 *   <li>{@link #put}: updates cache, cancels any pending write FOR THIS KEY, schedules a fresh one.</li>
 *   <li>{@link #get}: reads from cache (no I/O).</li>
 *   <li>{@link #evict}: cancels pending timer for the key, flushes its value on a virtual thread,
 *       removes from cache. Use on player disconnect so we never lose their last change.</li>
 *   <li>{@link #loadInto}: seed the cache from storage without scheduling a write. Use after
 *       a fresh read from the persistent store.</li>
 *   <li>{@link #flushAll}: flush every pending entry synchronously on the calling thread —
 *       intended for the shutdown hook so changes survive server stop.</li>
 * </ul>
 *
 * @param <K> cache key (use {@code UUID} per-player, or a constant like {@code "state"} for singletons)
 * @param <V> cached value
 */
public final class WriteBehindCache<K, V> {

    private final String name;
    private final Duration debounce;
    private final BiConsumer<K, V> writer;

    private final Map<K, V>                cache        = new ConcurrentHashMap<>();
    private final Map<K, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    public WriteBehindCache(String name, Duration debounce, BiConsumer<K, V> writer) {
        this.name = name;
        this.debounce = debounce;
        this.writer = writer;
    }

    /** Update cached value AND (re)schedule a debounced persist. Lock-free. */
    public void put(K key, V value) {
        cache.put(key, value);
        scheduleFlush(key);
    }

    /** Seed the cache without scheduling a write. Use after loading from persistent store. */
    public void loadInto(K key, V value) {
        cache.put(key, value);
    }

    public @Nullable V get(K key) {
        return cache.get(key);
    }

    public V getOrDefault(K key, V fallback) {
        return cache.getOrDefault(key, fallback);
    }

    /**
     * Cancel pending timer, flush current value on a virtual thread, and remove from cache.
     * Call this when the owner of {@code key} disconnects / is destroyed — guarantees the
     * very last change reaches storage even if it was made inside the debounce window.
     */
    public void evict(K key) {
        // Atomic remove-and-cancel: avoids racing with scheduleFlush for the same key.
        pendingTasks.computeIfPresent(key, (k, pending) -> {
            pending.cancel(false);
            return null;
        });
        V value = cache.remove(key);
        if (value != null) {
            // Virtual thread so we don't block any caller on file/Mongo latency.
            Thread.startVirtualThread(() -> writeNow(key, value));
        }
    }

    /** Synchronous flush of every pending entry — for shutdown hook. */
    public void flushAll() {
        for (var entry : pendingTasks.entrySet()) {
            entry.getValue().cancel(false);
            V v = cache.get(entry.getKey());
            if (v != null) writeNow(entry.getKey(), v);
        }
        pendingTasks.clear();
    }

    /**
     * Atomically swap the pending task for {@code key}: cancel any current one and install a new
     * timer. {@link Map#compute} guarantees no concurrent {@code put} on the SAME key can leak
     * an orphan ScheduledFuture — other keys' timers are never observed or modified.
     */
    private void scheduleFlush(K key) {
        pendingTasks.compute(key, (k, existing) -> {
            if (existing != null) existing.cancel(false);
            return BackgroundScheduler.SHARED.schedule(() -> {
                pendingTasks.remove(k);
                V value = cache.get(k);
                if (value == null) return;
                Thread.startVirtualThread(() -> writeNow(k, value));
            }, debounce.toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private void writeNow(K key, V value) {
        try {
            writer.accept(key, value);
        } catch (Exception e) {
            System.err.println("[WriteBehindCache:" + name + "] write failed for " + key + ": " + e.getMessage());
        }
    }
}
