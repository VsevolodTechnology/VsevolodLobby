package ua.vsevolod.lobby.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Legacy "&"-code parser + small cache for static strings.
 *
 * <h3>Cache discipline</h3>
 * <p>{@link #c(String)} is for <b>constant</b> strings — short text literals that recur many
 * times during the server lifetime. It is backed by a bounded {@link ConcurrentHashMap} so a
 * forgotten dynamic input cannot grow the heap forever; when the cache passes
 * {@link #CACHE_LIMIT} entries it is wiped and re-warmed lazily. The bound is purely defensive —
 * properly used code never reaches it.</p>
 *
 * <p>{@link #raw(String)} is for <b>dynamic</b> strings (player names, ping values, counts,
 * timers). It NEVER touches the cache and allocates a fresh component each call. Use this any
 * time the input depends on per-tick or per-player state.</p>
 */
public final class Text {

    /**
     * Maximum entries in the {@link #CACHE}. When exceeded, the cache is wiped — better than an
     * unbounded ConcurrentHashMap that would slowly leak Component objects (each tied to runtime
     * deserialiser state) over hours of uptime and cause a steady MSPT climb under GC pressure.
     */
    private static final int CACHE_LIMIT = 512;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final ConcurrentMap<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    /**
     * Cached parse — only for constant strings. Calling this with player-specific or other
     * dynamic data is a bug (eats memory).
     */
    public static Component c(String text) {
        Component cached = CACHE.get(text);
        if (cached != null) return cached;
        // Bounded defensive wipe — see CACHE_LIMIT doc above.
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(text, LEGACY::deserialize);
    }

    /** Uncached parse — use for dynamic strings (ping, online count, player names, …). */
    public static Component raw(String text) {
        return LEGACY.deserialize(text);
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
