package ua.vsevolod.lobby.feature.admin;

import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.WriteBehindCache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * Persisted set of operators (the names admitted to {@code BYPASS_USERS}).
 *
 * <p>{@link #save()} no longer blocks the tick — writes are buffered through
 * {@link WriteBehindCache} with a 3 s debounce. {@code /op A; /op B; /op C} in quick
 * succession results in a single disk write.</p>
 */
public final class OpsStore {

    private static final Path FILE = Paths.get("storage", "ops.txt");
    private static final String KEY = "state";

    private static final WriteBehindCache<String, List<String>> WRITER =
            new WriteBehindCache<>("OpsStore", Duration.ofSeconds(3), OpsStore::writeToDisk);

    private OpsStore() {}

    public static void load() {
        List<String> lines;
        try {
            if (!Files.exists(FILE)) return;
            lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[OpsStore] Failed to load ops: " + e.getMessage());
            return;
        }
        synchronized (OpsStore.class) {
            for (String raw : lines) {
                String name = raw.trim();
                if (!name.isEmpty() && !LobbyConfig.Settings.BYPASS_USERS.contains(name)) {
                    LobbyConfig.Settings.BYPASS_USERS.add(name);
                }
            }
        }
    }

    /** Public API stays the same — the implementation is now async. */
    public static void save() {
        List<String> snapshot;
        synchronized (OpsStore.class) {
            snapshot = List.copyOf(LobbyConfig.Settings.BYPASS_USERS);
        }
        WRITER.put(KEY, snapshot);
    }

    /** Shutdown hook: synchronously flush pending writes. */
    public static void flushAll() {
        WRITER.flushAll();
    }

    private static void writeToDisk(String ignored, List<String> snapshot) {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.write(tmp, snapshot, StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[OpsStore] Failed to save ops: " + e.getMessage());
        }
    }
}
