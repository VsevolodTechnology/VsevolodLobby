package ua.vsevolod.lobby.feature.lobby.player.prefs;

import java.util.UUID;

public interface PlayerDataStore extends AutoCloseable {
    /** Loads preferences for the given player, returning defaults if no record exists. */
    PlayerPreferences load(UUID uuid);

    /** Persists (upserts) preferences for the given player. */
    void save(UUID uuid, PlayerPreferences prefs);

    @Override
    void close();
}
