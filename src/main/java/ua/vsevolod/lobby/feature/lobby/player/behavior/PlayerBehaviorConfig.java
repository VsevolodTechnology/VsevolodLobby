package ua.vsevolod.lobby.feature.lobby.player.behavior;

/**
 * Runtime snapshot of {@code player-behavior.yml}.
 */
public record PlayerBehaviorConfig(boolean restoreLastPosition) {
    public static PlayerBehaviorConfig defaults() {
        return new PlayerBehaviorConfig(true);
    }
}
