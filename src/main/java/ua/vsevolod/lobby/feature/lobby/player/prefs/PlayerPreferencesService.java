package ua.vsevolod.lobby.feature.lobby.player.prefs;

import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.Nullable;
import ua.vsevolod.lobby.util.WriteBehindCache;

import java.time.Duration;
import java.util.UUID;

/**
 * Caches and persists per-player preferences (music, visibility, last position).
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #preload(UUID)} — called from {@code AsyncPlayerPreLoginEvent} (blocking OK
 *       there), loads from MongoDB and seeds the cache.</li>
 *   <li>{@link #get(UUID)} — cheap cache lookup used during {@code PlayerSpawnEvent}.</li>
 *   <li>{@code save*(…)} — update cache; the actual write is debounced 60 s on a virtual thread.</li>
 *   <li>{@link #evict(UUID)} — on disconnect: synchronously flush pending change (so we never
 *       lose the last toggle) and clear the cache entry.</li>
 *   <li>{@link #flushAll()} — call from the shutdown hook so changes survive server stop.</li>
 * </ol>
 *
 * <h3>Debounce</h3>
 * Each {@code save*()} pushes the deadline 60 s into the future. A player who toggles
 * continuously will never trigger a write while toggling — only after they stop, or when they
 * disconnect (which forces a flush). Idle players cost zero I/O. Anti-spam by construction.
 */
public final class PlayerPreferencesService {

    /** Debounce window: each toggle resets the timer. Long enough that bot-style spammers
     *  can't generate I/O — short enough that a normal session's final state is captured by
     *  the disconnect flush. */
    private static final Duration SAVE_DEBOUNCE = Duration.ofSeconds(60);

    @Nullable private final PlayerDataStore store;
    private final WriteBehindCache<UUID, PlayerPreferences> cache;

    public PlayerPreferencesService(@Nullable PlayerDataStore store) {
        this.store = store;
        this.cache = new WriteBehindCache<>("PlayerPrefs", SAVE_DEBOUNCE, (uuid, prefs) -> {
            if (store != null) store.save(uuid, prefs);
        });
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Blocking load — must be called from an async context (e.g. AsyncPlayerPreLoginEvent). */
    public void preload(UUID uuid) {
        if (store == null) return;
        try {
            cache.loadInto(uuid, store.load(uuid));
        } catch (Exception e) {
            System.err.println("[PlayerPrefs] Failed to load for " + uuid + ": " + e.getMessage());
        }
    }

    /** Returns cached preferences or {@link PlayerPreferences#defaults()} if not yet loaded. */
    public PlayerPreferences get(UUID uuid) {
        return cache.getOrDefault(uuid, PlayerPreferences.defaults());
    }

    /** On disconnect: flush pending changes synchronously (on virtual thread) and clear cache. */
    public void evict(UUID uuid) {
        cache.evict(uuid);
    }

    /** Shutdown hook: synchronously flush every player's pending changes. */
    public void flushAll() {
        cache.flushAll();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void saveMusicEnabled(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withMusicEnabled(v));
    }

    public void savePlayersHidden(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withPlayersHidden(v));
    }

    public void saveSidebarHidden(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withSidebarHidden(v));
    }

    public void savePositionSaveEnabled(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withPositionSaveEnabled(v));
    }

    public void saveProtocolWarningEnabled(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withProtocolWarningEnabled(v));
    }

    public void savePosition(UUID uuid, Pos position) {
        cache.put(uuid, get(uuid).withLastPosition(position));
    }

    public void saveTimeByIpEnabled(UUID uuid, boolean v) {
        cache.put(uuid, get(uuid).withTimeByIpEnabled(v));
    }

    /** Persist the full parkour-settings tuple in one shot. {@code null}/false values clear the entry. */
    public void saveParkour(UUID uuid,
                            @Nullable String difficulty,
                            @Nullable String theme,
                            @Nullable String dimension,
                            boolean training,
                            @Nullable String sound) {
        cache.put(uuid, get(uuid).withParkour(difficulty, theme, dimension, training, sound));
    }

    /**
     * Stamps {@code firstSeenEpoch} only if it is currently 0 (never seen). Returns the value
     * actually stored, so callers can decide whether this join is a real first-time visit.
     */
    public long markFirstSeenIfAbsent(UUID uuid) {
        PlayerPreferences cur = get(uuid);
        if (cur.firstSeenEpoch() != 0L) return cur.firstSeenEpoch();
        long now = System.currentTimeMillis();
        cache.put(uuid, cur.withFirstSeenEpoch(now));
        return now;
    }
}
