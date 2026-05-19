package ua.vsevolod.lobby.feature.lobby.player.behavior;

import ua.vsevolod.lobby.feature.admin.config.ConfigSection;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Config section for {@code player-behavior.yml}.
 *
 * <pre>
 * player_behavior:
 *   restore_last_position: true   # teleport player to their last known position on rejoin
 * </pre>
 */
public final class PlayerBehaviorConfigSection implements ConfigSection<PlayerBehaviorConfig> {

    public static final PlayerBehaviorConfigSection INSTANCE = new PlayerBehaviorConfigSection();

    private static final String TEMPLATE = """
            # ====================================================
            # Настройки поведения игроков — player-behavior.yml
            # ====================================================
            # После изменения файла выполни /reload.

            player_behavior:
              # true  = при повторном входе игрок телепортируется на последнюю позицию
              #         (та точка, где он стоял при выходе с лобби).
              # false = игрок всегда появляется в стандартном спавне лобби.
              # Примечание: позиция сохраняется в MongoDB. Если MongoDB недоступна,
              # настройка игнорируется и игрок спавнится в стандартном месте.
              restore_last_position: true
            """;

    private final AtomicReference<PlayerBehaviorConfig> current =
            new AtomicReference<>(PlayerBehaviorConfig.defaults());

    private PlayerBehaviorConfigSection() {}

    public PlayerBehaviorConfig current() { return current.get(); }

    @Override
    public String name() { return "player-behavior"; }

    @Override
    public String templateYaml() { return TEMPLATE; }

    @Override
    public PlayerBehaviorConfig parse(Map<String, Object> yaml) {
        Object section = yaml.get("player_behavior");
        if (!(section instanceof Map<?, ?> m)) return PlayerBehaviorConfig.defaults();

        Object raw = m.get("restore_last_position");
        boolean restore = raw instanceof Boolean b ? b
                : raw instanceof String s && Boolean.parseBoolean(s.trim());
        return new PlayerBehaviorConfig(restore);
    }

    @Override
    public void apply(PlayerBehaviorConfig snapshot) {
        current.set(snapshot);
    }
}
