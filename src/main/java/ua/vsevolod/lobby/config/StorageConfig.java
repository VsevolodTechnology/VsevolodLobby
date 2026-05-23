package ua.vsevolod.lobby.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import de.exlll.configlib.YamlConfigurations;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Storage-backend settings backed by {@code config/storage.yml}.
 *
 * <p>Defaults to {@link Mode#FILE} everywhere — MongoDB is never touched unless explicitly
 * opted into by setting {@link #playerPrefs} or {@link #leaderboard} to {@code MONGODB}.</p>
 */
@Configuration
public final class StorageConfig {

    public enum Mode { FILE, MONGODB }

    private static final Path CONFIG_FILE = Paths.get("config", "storage.yml");
    private static volatile StorageConfig INSTANCE;

    @Comment("Player preferences storage. Allowed: FILE, MONGODB.")
    public Mode playerPrefs = Mode.FILE;

    @Comment("Parkour leaderboard storage. Allowed: FILE, MONGODB.")
    public Mode leaderboard = Mode.FILE;

    @Comment("MongoDB connection URI. Used only when a backend above is MONGODB.")
    public String mongoUri = "mongodb://127.0.0.1:27017";

    @Comment("MongoDB database name.")
    public String mongoDatabase = "overdyn";

    @Comment("MongoDB collection used for the parkour leaderboard.")
    public String mongoLeaderboardCollection = "parkour_leaderboard";

    @Comment("Fall back to local file storage if MongoDB is unreachable.")
    public boolean mongoFallbackToFile = true;

    public static StorageConfig get() {
        StorageConfig c = INSTANCE;
        if (c != null) return c;
        synchronized (StorageConfig.class) {
            if (INSTANCE == null) INSTANCE = load();
            return INSTANCE;
        }
    }

    public static StorageConfig load() {
        StorageConfig cfg;
        try {
            cfg = YamlConfigurations.update(CONFIG_FILE, StorageConfig.class);
        } catch (Exception e) {
            System.err.println("[StorageConfig] Failed to load " + CONFIG_FILE + ": " + e.getMessage() + " — using FILE defaults");
            cfg = new StorageConfig();
        }
        INSTANCE = cfg;
        return cfg;
    }

    public static StorageConfig defaults() {
        return new StorageConfig();
    }
}
