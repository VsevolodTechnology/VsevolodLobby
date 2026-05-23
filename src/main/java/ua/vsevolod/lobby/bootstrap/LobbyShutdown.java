package ua.vsevolod.lobby.bootstrap;

import ua.vsevolod.lobby.feature.admin.OpsStore;
import ua.vsevolod.lobby.feature.admin.VersionGate;
import ua.vsevolod.lobby.feature.lobby.player.chat.ChatState;

import java.util.ArrayList;
import java.util.List;

/**
 * Centralised shutdown drain: forces every {@link ua.vsevolod.lobby.util.WriteBehindCache}
 * to flush pending writes to disk before the process exits.
 *
 * <p>Called from every code path that stops the server: {@code RestartCommand},
 * {@code StopCommand}, and the JVM {@code ShutdownHook}. Without this, a player who toggled
 * a setting within the debounce window before {@code stopCleanly()} would lose their change.</p>
 *
 * <p>Static caches ({@link ChatState}, {@link OpsStore}, {@link VersionGate}) are flushed
 * directly; instance services that aren't statically reachable (e.g.
 * {@code PlayerPreferencesService}) register themselves via {@link #register(Runnable)}.</p>
 */
public final class LobbyShutdown {

    private static final List<Runnable> HOOKS = new ArrayList<>();

    private LobbyShutdown() {}

    /** Register a flush hook from an instance service (e.g. PlayerPreferencesService::flushAll). */
    public static synchronized void register(Runnable hook) {
        HOOKS.add(hook);
    }

    /** Drain every pending write to disk. Idempotent — safe to call from multiple shutdown paths. */
    public static void flushAllPersistence() {
        ChatState.flushAll();
        OpsStore.flushAll();
        VersionGate.flushAll();
        List<Runnable> snapshot;
        synchronized (LobbyShutdown.class) {
            snapshot = new ArrayList<>(HOOKS);
        }
        for (Runnable hook : snapshot) {
            try { hook.run(); }
            catch (Exception e) {
                System.err.println("[LobbyShutdown] hook failed: " + e.getMessage());
            }
        }
    }
}
