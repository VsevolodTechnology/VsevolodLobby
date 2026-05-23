package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * LuckPerms-Minestom integration toggle. Backed by {@code config/system/luckperms.yml}.
 *
 * <p>When {@link #enabled} is {@code false} (default), the LP runtime is not started at all
 * and {@code AdminCommand.isAdmin} falls back to the hardcoded {@code BYPASS_USERS} set.
 * When enabled, LP is initialised with its data directory under {@link #dataDirectory} and
 * registers its own {@code /luckperms} command for in-game admin management.</p>
 *
 * <p>This config is read once at boot — toggling {@link #enabled} requires a restart because
 * LP isn't safe to start/stop on a live server. The other fields are picked up at init time.</p>
 */
@Configuration
public final class LuckPermsConfig {

    private static final Path CONFIG_FILE = Paths.get("config", "system", "luckperms.yml");

    @Comment({
            "Включает интеграцию с LuckPerms-Minestom.",
            "false — пермы определяются только встроенным BYPASS_USERS списком (как раньше).",
            "true  — LP стартует в /lp, читает свою БД из dataDirectory, и админ-команды",
            "        дополнительно проверяют пермишен 'orjus.admin'. Смена этого флага",
            "        требует рестарта сервера."
    })
    public boolean enabled = false;

    @Comment({
            "Папка с данными LP (storage, locale, config).",
            "При первом запуске LP создаст в ней свой конфиг и базу (по умолчанию H2)."
    })
    public String dataDirectory = "storage/luckperms";

    @Comment({
            "Регистрировать ли /luckperms (и /lp) в игре.",
            "true — стандартное LP-меню управления группами / пермами доступно из чата.",
            "false — LP работает, но команда не регится (если не нужна)."
    })
    public boolean registerCommands = true;

    @Comment({
            "Пермишен, который проверяется в качестве админского.",
            "Если игрок имеет этот перм через LP — он считается админом наравне с BYPASS_USERS."
    })
    public String adminPermission = "orjus.admin";

    public static LuckPermsConfig load() {
        try {
            return YamlConfigurations.update(CONFIG_FILE, LuckPermsConfig.class);
        } catch (Exception e) {
            System.err.println("[LuckPermsConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using defaults (disabled)");
            return new LuckPermsConfig();
        }
    }
}
