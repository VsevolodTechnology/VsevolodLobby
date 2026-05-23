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
 *
 * <p>The parkour fields and {@code timeByIpEnabled} used to live in their own flat files
 * ({@code storage/parkour-prefs.properties}, {@code storage/time-by-ip.txt}) — they were folded
 * in so every per-player setting goes through the same store and disconnect-flush.</p>
 */
public record PlayerPreferences(
        boolean musicEnabled,
        boolean playersHidden,
        boolean sidebarHidden,
        boolean positionSaveEnabled,
        boolean protocolWarningEnabled,
        long firstSeenEpoch,
        @Nullable Pos lastPosition,
        boolean timeByIpEnabled,
        @Nullable String parkourDifficulty,
        @Nullable String parkourTheme,
        @Nullable String parkourDimension,
        boolean parkourTraining,
        @Nullable String parkourSound
) {
    public static PlayerPreferences defaults() {
        return new PlayerPreferences(true, false, false, true, true, 0L, null,
                false, null, null, null, false, null);
    }

    public PlayerPreferences withMusicEnabled(boolean v) {
        return new PlayerPreferences(v, playersHidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withPlayersHidden(boolean v) {
        return new PlayerPreferences(musicEnabled, v, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withSidebarHidden(boolean v) {
        return new PlayerPreferences(musicEnabled, playersHidden, v, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withPositionSaveEnabled(boolean v) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, v, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withProtocolWarningEnabled(boolean v) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, positionSaveEnabled, v, firstSeenEpoch, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withFirstSeenEpoch(long v) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, v, lastPosition,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withLastPosition(@Nullable Pos v) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, v,
                timeByIpEnabled, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withTimeByIpEnabled(boolean v) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                v, parkourDifficulty, parkourTheme, parkourDimension, parkourTraining, parkourSound);
    }

    public PlayerPreferences withParkour(@Nullable String difficulty, @Nullable String theme,
                                         @Nullable String dimension, boolean training,
                                         @Nullable String sound) {
        return new PlayerPreferences(musicEnabled, playersHidden, sidebarHidden, positionSaveEnabled, protocolWarningEnabled, firstSeenEpoch, lastPosition,
                timeByIpEnabled, difficulty, theme, dimension, training, sound);
    }

    public boolean hasParkour() {
        return parkourDifficulty != null;
    }
}
