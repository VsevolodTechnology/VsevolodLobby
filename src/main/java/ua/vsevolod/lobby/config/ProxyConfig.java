package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Proxy / network-binding settings backed by {@code config/proxy.yml}.
 *
 * <p>Migrated from the legacy {@code proxy.properties} file — ConfigLib auto-creates the YAML
 * file on first run with field defaults and the comments below.</p>
 */
@Configuration
public final class ProxyConfig {

    private static final Path CONFIG_FILE = Paths.get("config", "network", "proxy.yml");

    @Comment({
            "Velocity modern-forwarding secret — copy the value from Velocity's `forwarding.secret`.",
            "When non-empty, Minestom will only accept connections forwarded by that Velocity instance.",
            "Leave empty to run without Velocity (plain offline mode)."
    })
    public String velocityForwardingSecret = "";

    @Comment({
            "Bind address for the Minecraft listener.",
            "- If Velocity is on the SAME machine, set 127.0.0.1 so the backend is not exposed.",
            "- If Velocity is on a DIFFERENT machine, keep 0.0.0.0 and firewall to Velocity's IP."
    })
    public String hostAddress = "0.0.0.0";

    @Comment("Bind port. Must match the port configured in velocity.toml for this backend.")
    public int hostPort = 25565;

    @Comment({
            "ЖЁСТКИЙ предел — выше него обычные игроки не пройдут login-гейт.",
            "Игроки из BYPASS_USERS (админы) этот лимит игнорируют — могут зайти даже при 300/300.",
            "Меняется в игре через /maxplayers <n>."
    })
    public int maxPlayers = 100;

    @Comment({
            "Динамический MOTD: показанный «макс» растёт вместе с реальным онлайном.",
            "true  — MOTD показывает min(maxPlayers, max(displayBaseline, online + displayHeadroom)).",
            "false — MOTD всегда показывает maxPlayers (старое поведение)."
    })
    public boolean dynamicMaxEnabled = true;

    @Comment({
            "Нижняя граница отображаемого максимума.",
            "Например, при онлайне = 4 и baseline = 10 MOTD покажет 4/10 — не «4/3»."
    })
    public int displayBaseline = 10;

    @Comment({
            "Запас сверху над текущим онлайном.",
            "При онлайне 12 и headroom 3 MOTD покажет 12/15 — пока не упрётся в maxPlayers."
    })
    public int displayHeadroom = 3;

    @Comment({
            "Embedded ViaProxy bridge for legacy-client (≤ 1.20) support.",
            "When enabled, the bundled ViaProxy listens on hostPort and forwards translated",
            "traffic to Minestom which binds on 127.0.0.1:internalPort. When disabled (default),",
            "Minestom binds directly to hostPort — no extra process, no extra latency."
    })
    public boolean viaProxyEnabled = false;

    @Comment("Internal loopback port Minestom binds to when viaProxyEnabled = true.")
    public int internalPort = 25566;

    @Comment({
            "Target Minecraft version reported to ViaProxy (the version Minestom speaks).",
            "Use \"auto\" to let ViaProxy probe at startup, or pin an exact version like \"1.21.11\"."
    })
    public String viaTargetVersion = "auto";

    public boolean velocityEnabled() {
        return velocityForwardingSecret != null && !velocityForwardingSecret.isBlank();
    }

    public String velocitySecret() {
        return velocityForwardingSecret;
    }

    public String hostAddress() {
        return hostAddress;
    }

    public int hostPort() {
        return hostPort;
    }

    public boolean viaProxyEnabled() {
        return viaProxyEnabled;
    }

    public int internalPort() {
        return internalPort;
    }

    public String viaTargetVersion() {
        return viaTargetVersion;
    }

    public static ProxyConfig load() {
        ProxyConfig cfg;
        try {
            cfg = YamlConfigurations.update(CONFIG_FILE, ProxyConfig.class);
        } catch (Exception e) {
            System.err.println("[ProxyConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using defaults");
            cfg = new ProxyConfig();
        }
        // Propagate to legacy mutable static used by tab/MOTD code paths.
        LobbyConfig.Settings.MAX_PLAYERS         = cfg.maxPlayers;
        LobbyConfig.Settings.DYNAMIC_MAX_ENABLED = cfg.dynamicMaxEnabled;
        LobbyConfig.Settings.DISPLAY_BASELINE    = cfg.displayBaseline;
        LobbyConfig.Settings.DISPLAY_HEADROOM    = cfg.displayHeadroom;
        return cfg;
    }

    /**
     * Persist a new {@link #maxPlayers} value to {@code config/network/proxy.yml} and update
     * the in-memory {@link LobbyConfig.Settings#MAX_PLAYERS} so the change is visible
     * immediately (MOTD, login gate, server-list ping). Used by {@code /maxplayers <n>}.
     */
    public static synchronized void setMaxPlayers(int newValue) {
        ProxyConfig cfg = YamlConfigurations.update(CONFIG_FILE, ProxyConfig.class);
        cfg.maxPlayers = newValue;
        YamlConfigurations.save(CONFIG_FILE, ProxyConfig.class, cfg);
        LobbyConfig.Settings.MAX_PLAYERS = newValue;
    }
}
