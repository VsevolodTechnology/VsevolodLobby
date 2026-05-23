package ua.vsevolod.lobby.feature.lobby.player.prefs;

import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent per-player preferences loaded on login and written back on change.
 *
 * <p>{@code firstSeenEpoch} is the unix millis of the player's very first server join. It is
 * set on the first load when the previous value is 0 (the {@link #defaults() defaults sentinel}
 * indicating "never seen") and is what the welcome animation reads to compute the "ты с нами N
 * дней" line.</p>
 */
public record PlayerPreferences(
        boolean musicEnabled,
        boolean playersHidden,
        boolean sidebarHidden,
        boolean positionSaveEnabled,
        boolean protocolWarningEnabled,
        long firstSeenEpoch,
        @Nullable Pos lastPosition
) {
    public static PlayerPreferences defaults() {
        return new PlayerPreferences(true, false, false, true, true, 0L, null);
    }
}
