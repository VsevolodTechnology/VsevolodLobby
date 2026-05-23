package ua.vsevolod.lobby.feature.admin;

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
 * Tracks the protocol-version filter (enabled flag + min/max range).
 *
 * <p>State is held in {@code volatile} fields for lock-free reads; writes go through a
 * {@link WriteBehindCache} with a 3 s debounce. Multiple admin edits ({@code /version min 770;
 * /version max 776; /version on}) coalesce into a single file write.</p>
 */
public final class VersionGate {

    private static final Path FILE = Paths.get("storage", "version_gate.txt");
    private static final String KEY = "state";

    private static volatile boolean enabled = false;
    private static volatile int minProtocol = 0;
    private static volatile int maxProtocol = 999_999;

    private static final WriteBehindCache<String, State> WRITER =
            new WriteBehindCache<>("VersionGate", Duration.ofSeconds(3), VersionGate::writeToDisk);

    private VersionGate() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getMin() {
        return minProtocol;
    }

    public static int getMax() {
        return maxProtocol;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        scheduleWrite();
    }

    public static void setMin(int value) {
        minProtocol = value;
        scheduleWrite();
    }

    public static void setMax(int value) {
        maxProtocol = value;
        scheduleWrite();
    }

    public static boolean allows(int protocol) {
        if (!enabled) return true;
        return protocol >= minProtocol && protocol <= maxProtocol;
    }

    /** One-time load at startup. Blocking is fine here (called from main thread before
     *  Minestom.init), and we want the file read to complete before any client can connect. */
    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                switch (key) {
                    case "enabled" -> enabled = Boolean.parseBoolean(value);
                    case "min" -> {
                        try { minProtocol = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                    }
                    case "max" -> {
                        try { maxProtocol = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[VersionGate] Failed to load: " + e.getMessage());
        }
    }

    /** Shutdown hook: synchronously flush pending writes. */
    public static void flushAll() {
        WRITER.flushAll();
    }

    private static void scheduleWrite() {
        WRITER.put(KEY, new State(enabled, minProtocol, maxProtocol));
    }

    private static void writeToDisk(String ignored, State state) {
        try {
            Files.createDirectories(FILE.getParent());
            String content =
                    "enabled=" + state.enabled + "\n" +
                            "min=" + state.min + "\n" +
                            "max=" + state.max + "\n";
            // Atomic tmp+move — a crash during the prior writeString could leave the file
            // partially written. With this gate, on next load we'd read back broken state and
            // potentially kick legitimate clients. Same pattern as ConfigManager / OpsStore.
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[VersionGate] Failed to save: " + e.getMessage());
        }
    }

    private record State(boolean enabled, int min, int max) {}
}
