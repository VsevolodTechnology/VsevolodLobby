package ua.vsevolod.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy "&"-code parser + small bounded LRU cache for static strings.
 *
 * <h3>Cache discipline</h3>
 * <p>{@link #c(String)} is for <b>constant</b> strings — short text literals that recur many
 * times during the server lifetime. It is backed by a bounded LRU map so a forgotten dynamic
 * input cannot grow the heap forever; when the cache passes {@link #CACHE_LIMIT} entries the
 * least-recently used entry is evicted (not the whole map — the previous {@code clear()} on
 * overflow stuttered re-warming on workloads near the limit. Audit LOW-02 fix).</p>
 *
 * <p>{@link #raw(String)} is for <b>dynamic</b> strings (player names, ping values, counts,
 * timers). It NEVER touches the cache and allocates a fresh component each call. Use this any
 * time the input depends on per-tick or per-player state.</p>
 */
public final class Text {

    private static final int CACHE_LIMIT = 512;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /**
     * Insertion-order {@link LinkedHashMap} in access-order mode acts as an LRU when wrapped
     * with the {@link #removeEldestEntry} override. Synchronized-wrapped because the map is
     * mutated under both read (computeIfAbsent) and write (eviction) paths from any thread —
     * a plain {@link java.util.concurrent.ConcurrentHashMap} doesn't give us the eviction
     * semantics for free.
     */
    private static final Map<String, Component> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Component>(CACHE_LIMIT + 16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                    return size() > CACHE_LIMIT;
                }
            }
    );

    private Text() {
    }

    /**
     * Cached parse — only for constant strings. Calling this with player-specific or other
     * dynamic data is a bug (eats cache slots; the LRU will spend its budget on garbage
     * instead of the strings that actually repeat).
     */
    public static Component c(String text) {
        // synchronized via Collections.synchronizedMap wrapper above; LinkedHashMap.get in
        // access-order mode mutates internal links, so plain unsync reads are unsafe.
        synchronized (CACHE) {
            Component cached = CACHE.get(text);
            if (cached != null) return cached;
            Component fresh = LEGACY.deserialize(text);
            CACHE.put(text, fresh);
            return fresh;
        }
    }

    /** Uncached parse — use for dynamic strings (ping, online count, player names, …). */
    public static Component raw(String text) {
        return LEGACY.deserialize(text);
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
