package ua.vsevolod.lobby.feature.lobby.player.prefs;

import net.minestom.server.coordinate.Pos;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent per-player preferences loaded from MongoDB on login and written back on change.
 */
public record PlayerPreferences(
        boolean musicEnabled,
        boolean playersHidden,
        boolean sidebarHidden,
        boolean positionSaveEnabled,
        @Nullable Pos lastPosition
) {
    public static PlayerPreferences defaults() {
        return new PlayerPreferences(true, false, false, true, null);
    }
}
