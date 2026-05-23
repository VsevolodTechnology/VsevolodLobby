package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;

/**
 * Logging settings backed by {@code config/logging.yml}.
 *
 * <p>The {@link #timezone} field is exposed as a {@link ZoneId} through {@link #zoneId()}
 * — the raw YAML value is a string. Invalid zones fall back to UTC at access time, never
 * at load time, so a typo in the file doesn't take the server down.</p>
 */
@Configuration
public final class LoggingConfig {

    private static final Path CONFIG_FILE = Paths.get("config", "logging.yml");

    @Comment({
            "Java time-zone id used for timestamps in console / log files.",
            "Examples: \"UTC\", \"Europe/Kyiv\", \"America/New_York\"."
    })
    public String timezoneId = "UTC";

    @Comment("Write formatted logs to console (startup/stop/player events are always printed).")
    public boolean toConsole = true;

    @Comment("Write detailed logs to logs/latest.log and logs/YYYY-MM-DD.log.")
    public boolean toFile = true;

    @Comment("Log player connection lines to console.")
    public boolean logPlayerConnect = true;

    @Comment("Log player disconnection lines to console.")
    public boolean logPlayerDisconnect = true;

    @Comment("Log player mode-selection events.")
    public boolean logPlayerMode = true;

    @Comment("Log parkour leaderboard events.")
    public boolean logLeaderboardEvents = true;

    @Comment("Enable debug-level messages in the file log.")
    public boolean debugLogs = false;

    // ── Read-side fields kept for source-compat with existing call sites ───
    /** Resolved zone — cached on first access. Stays final-style: read it, don't write it. */
    public transient ZoneId timezone = ZoneId.of("UTC");

    public ZoneId zoneId() {
        return timezone;
    }

    public static LoggingConfig load() {
        LoggingConfig cfg;
        try {
            cfg = YamlConfigurations.update(CONFIG_FILE, LoggingConfig.class);
        } catch (Exception e) {
            System.err.println("[LoggingConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using defaults");
            cfg = new LoggingConfig();
        }
        cfg.timezone = parseTimezone(cfg.timezoneId);
        return cfg;
    }

    public static LoggingConfig defaults() {
        LoggingConfig cfg = new LoggingConfig();
        cfg.timezone = ZoneId.of("UTC");
        return cfg;
    }

    private static ZoneId parseTimezone(String raw) {
        try {
            return ZoneId.of(raw.trim());
        } catch (Exception e) {
            System.err.println("[LoggingConfig] Unknown timezone '" + raw + "', falling back to UTC");
            return ZoneId.of("UTC");
        }
    }
}
