package ua.vsevolod.lobby.feature.admin;

import ua.vsevolod.lobby.config.LobbyConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class OpsStore {

    private static final Path FILE = Paths.get("storage", "ops.txt");

    private OpsStore() {}

    public static synchronized void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String name = raw.trim();
                if (!name.isEmpty() && !LobbyConfig.Settings.BYPASS_USERS.contains(name)) {
                    LobbyConfig.Settings.BYPASS_USERS.add(name);
                }
            }
        } catch (IOException e) {
            System.err.println("[OpsStore] Failed to load ops: " + e.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(
                    FILE,
                    LobbyConfig.Settings.BYPASS_USERS,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.err.println("[OpsStore] Failed to save ops: " + e.getMessage());
        }
    }
}
