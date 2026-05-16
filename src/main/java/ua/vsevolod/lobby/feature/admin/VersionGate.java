package ua.vsevolod.lobby.feature.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class VersionGate {

    private static final Path FILE = Paths.get("storage", "version_gate.txt");

    private static volatile boolean enabled = false;
    private static volatile int minProtocol = 0;
    private static volatile int maxProtocol = 999_999;

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

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static synchronized void setMin(int value) {
        minProtocol = value;
        save();
    }

    public static synchronized void setMax(int value) {
        maxProtocol = value;
        save();
    }

    public static boolean allows(int protocol) {
        if (!enabled) return true;
        return protocol >= minProtocol && protocol <= maxProtocol;
    }

    public static synchronized void load() {
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

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            String content =
                    "enabled=" + enabled + "\n" +
                            "min=" + minProtocol + "\n" +
                            "max=" + maxProtocol + "\n";
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
}
