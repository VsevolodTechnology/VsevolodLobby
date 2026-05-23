package ua.vsevolod.lobby.feature.lobby.player.behavior;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import ua.vsevolod.lobby.config.ConfigReload;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Player-behaviour config, backed by {@code config/player-behavior.yml}.
 * ConfigLib-powered, hot-reloadable via {@link ConfigReload}.
 */
@Configuration
public final class PlayerBehaviorConfig {

    private static final Path FILE = Paths.get("config", "player", "player-behavior.yml");
    private static volatile PlayerBehaviorConfig instance;

    @Comment({
            "true  — при повторном входе игрок телепортируется на последнюю позицию.",
            "false — игрок всегда появляется в стандартном спавне лобби.",
            "Позиция хранится в player_data; если хранилище недоступно — игнорируется."
    })
    public boolean restoreLastPosition = true;

    public static PlayerBehaviorConfig get() {
        PlayerBehaviorConfig c = instance;
        return c != null ? c : load();
    }

    public static synchronized PlayerBehaviorConfig load() {
        try {
            instance = YamlConfigurations.update(FILE, PlayerBehaviorConfig.class);
        } catch (Exception e) {
            System.err.println("[PlayerBehaviorConfig] Failed to load " + FILE + ": " + e.getMessage() + " — defaults");
            if (instance == null) instance = new PlayerBehaviorConfig();
        }
        return instance;
    }

    public static void init() {
        load();
        ConfigReload.register("player-behavior", PlayerBehaviorConfig::load);
    }
}
