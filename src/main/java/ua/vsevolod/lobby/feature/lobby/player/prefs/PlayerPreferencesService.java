package ua.vsevolod.lobby.feature.lobby.player.prefs;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.timer.Task;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches and persists per-player preferences (music, visibility, last position).
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #preload(UUID)} — called from {@code AsyncPlayerPreLoginEvent} (blocking OK
 *       there), loads from MongoDB and caches.</li>
 *   <li>{@link #get(UUID)} — cheap cache lookup used during {@code PlayerSpawnEvent}.</li>
 *   <li>{@code save*(…)} — update cache and schedule a debounced persist on a virtual thread.</li>
 *   <li>{@link #evict(UUID)} — clean up cache on disconnect (also cancels pending save).</li>
 * </ol>
 *
 * <h3>Debounce</h3>
 * Rapid successive toggles (e.g. fast GUI clicks) are coalesced: the actual MongoDB write is
 * delayed 400 ms after the last change.  Only one pending write task exists per player at any
 * time, so 10 quick clicks → 1 database write instead of 10.
 */
public final class PlayerPreferencesService {

    private static final long SAVE_DEBOUNCE_MS = 400;

    @Nullable private final PlayerDataStore store;
    private final ConcurrentHashMap<UUID, PlayerPreferences> cache       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Task>              pendingSaves = new ConcurrentHashMap<>();

    public PlayerPreferencesService(@Nullable PlayerDataStore store) {
        this.store = store;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Blocking load — must be called from an async context (e.g. AsyncPlayerPreLoginEvent). */
    public void preload(UUID uuid) {
        if (store == null) return;
        try {
            cache.put(uuid, store.load(uuid));
        } catch (Exception e) {
            System.err.println("[PlayerPrefs] Failed to load for " + uuid + ": " + e.getMessage());
        }
    }

    /** Returns cached preferences or {@link PlayerPreferences#defaults()} if not yet loaded. */
    public PlayerPreferences get(UUID uuid) {
        return cache.getOrDefault(uuid, PlayerPreferences.defaults());
    }

    /** Remove from cache on disconnect; also cancels any pending debounced save. */
    public void evict(UUID uuid) {
        cache.remove(uuid);
        Task pending = pendingSaves.remove(uuid);
        if (pending != null) pending.cancel();
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void saveMusicEnabled(UUID uuid, boolean musicEnabled) {
        PlayerPreferences cur = get(uuid);
        update(uuid, new PlayerPreferences(musicEnabled, cur.playersHidden(), cur.sidebarHidden(), cur.positionSaveEnabled(), cur.lastPosition()));
    }

    public void savePlayersHidden(UUID uuid, boolean playersHidden) {
        PlayerPreferences cur = get(uuid);
        update(uuid, new PlayerPreferences(cur.musicEnabled(), playersHidden, cur.sidebarHidden(), cur.positionSaveEnabled(), cur.lastPosition()));
    }

    public void saveSidebarHidden(UUID uuid, boolean sidebarHidden) {
        PlayerPreferences cur = get(uuid);
        update(uuid, new PlayerPreferences(cur.musicEnabled(), cur.playersHidden(), sidebarHidden, cur.positionSaveEnabled(), cur.lastPosition()));
    }

    public void savePositionSaveEnabled(UUID uuid, boolean positionSaveEnabled) {
        PlayerPreferences cur = get(uuid);
        update(uuid, new PlayerPreferences(cur.musicEnabled(), cur.playersHidden(), cur.sidebarHidden(), positionSaveEnabled, cur.lastPosition()));
    }

    public void savePosition(UUID uuid, Pos position) {
        PlayerPreferences cur = get(uuid);
        update(uuid, new PlayerPreferences(cur.musicEnabled(), cur.playersHidden(), cur.sidebarHidden(), cur.positionSaveEnabled(), position));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void update(UUID uuid, PlayerPreferences prefs) {
        cache.put(uuid, prefs);
        if (store == null) return;

        // Cancel any existing pending write for this player
        Task existing = pendingSaves.remove(uuid);
        if (existing != null) existing.cancel();

        // Schedule a single debounced write 400 ms after the last change
        Task task = MinecraftServer.getSchedulerManager()
                .buildTask(() -> {
                    pendingSaves.remove(uuid);
                    try {
                        store.save(uuid, prefs);
                    } catch (Exception e) {
                        System.err.println("[PlayerPrefs] Failed to save for " + uuid + ": " + e.getMessage());
                    }
                })
                .delay(Duration.ofMillis(SAVE_DEBOUNCE_MS))
                .schedule();
        pendingSaves.put(uuid, task);
    }
}
