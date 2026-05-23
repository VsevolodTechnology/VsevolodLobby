package ua.vsevolod.lobby.feature.admin.restart;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;
import net.kyori.adventure.text.Component;
import ua.vsevolod.lobby.util.Text;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Restart settings backed by {@code config/restart.yml}.
 *
 * <p>Loaded once on demand by {@link #get()} and rebuilt by {@link #reload()} after admin edits.
 * Hot-reload is safe — every accessor reads the current snapshot through the {@code volatile}
 * reference, so a countdown in progress sees consistent values.</p>
 */
@Configuration
public final class RestartConfig {

    private static final Path CONFIG_FILE = Paths.get("config", "restart.yml");
    private static volatile RestartConfig INSTANCE;

    @Comment("Default countdown (seconds) when /restart is invoked without arguments.")
    public int defaultCountdownSeconds = 30;

    @Comment("Hard upper bound to keep admins from typing /restart 99999 by accident.")
    public int maxCountdownSeconds = 600;

    @Comment({
            "Kick message shown on the disconnect screen when the restart fires.",
            "Supports legacy '&' codes, '&#RRGGBB' hex, and '\\n' for newlines."
    })
    public String kickMessageLegacy =
            "<#AE3AF3>Сервер перезапускается.\\n<gray>Попробуйте зайти через несколько секунд.";

    public static RestartConfig get() {
        RestartConfig c = INSTANCE;
        if (c != null) return c;
        synchronized (RestartConfig.class) {
            if (INSTANCE == null) INSTANCE = load();
            return INSTANCE;
        }
    }

    public static RestartConfig reload() {
        return INSTANCE = load();
    }

    private static RestartConfig load() {
        try {
            return YamlConfigurations.update(CONFIG_FILE, RestartConfig.class);
        } catch (Exception e) {
            System.err.println("[RestartConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using defaults");
            return new RestartConfig();
        }
    }

    // ── Convenience accessors ────────────────────────────────────────────────

    public static int defaultCountdown() {
        return get().defaultCountdownSeconds;
    }

    public static int maxCountdown() {
        return get().maxCountdownSeconds;
    }

    /** Parsed kick-screen component. {@code \n} in YAML becomes a real newline. */
    public static Component kickMessage() {
        String legacy = get().kickMessageLegacy.replace("\\n", "\n");
        return Text.raw(legacy);
    }
}
