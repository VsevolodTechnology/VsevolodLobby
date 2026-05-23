package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Chat settings backed by {@code config/chat.yml}.
 *
 * <p>Class-based config powered by ConfigLib — missing keys are auto-filled with the field
 * defaults below and the file is rewritten with comments preserved. New fields added in code
 * automatically appear in the YAML on next load; obsolete keys are removed.</p>
 */
@Configuration
public final class ChatConfig {

    private static final Path CONFIG_FILE = Paths.get("config", "player", "chat.yml");
    private static volatile ChatConfig INSTANCE;

    @Comment({
            "Message format — placeholders: {player}, {message}",
            "Supports legacy '&' color codes and &#RRGGBB hex colors."
    })
    public String format = "<#FFF2E0>{player} <dark_gray>» <gray>{message}";

    @Comment("Maximum characters per message (0 = unlimited).")
    public int maxLength = 256;

    @Comment("Anti-spam cooldown in seconds between messages (0 = disabled).")
    public int cooldownSeconds = 2;

    @Comment("Master switch for the anti-advertising filter.")
    public boolean filterEnabled = true;

    @Comment("Block http(s):// and ftp:// links.")
    public boolean blockLinks = true;

    @Comment("Block domain patterns (.com .net .ru .gg etc.).")
    public boolean blockDomains = true;

    @Comment("Block raw IPv4 addresses (1.2.3.4).")
    public boolean blockIps = true;

    @Comment("Detect obfuscated ads (spaced letters, (dot) notation, etc.).")
    public boolean blockObfuscated = true;

    @Comment("Message sent to the player whose message was blocked.")
    public String warnMessage = "<red>Ваше сообщение заблокировано: реклама запрещена.";

    public static ChatConfig get() {
        ChatConfig c = INSTANCE;
        if (c != null) return c;
        synchronized (ChatConfig.class) {
            if (INSTANCE == null) INSTANCE = load();
            return INSTANCE;
        }
    }

    /** Loads from disk, creating / updating the file with field defaults as needed. */
    public static ChatConfig load() {
        ChatConfig cfg;
        try {
            cfg = YamlConfigurations.update(CONFIG_FILE, ChatConfig.class);
        } catch (Exception e) {
            System.err.println("[ChatConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using defaults");
            cfg = new ChatConfig();
        }
        INSTANCE = cfg;
        return cfg;
    }

    public static ChatConfig defaults() {
        return new ChatConfig();
    }
}
