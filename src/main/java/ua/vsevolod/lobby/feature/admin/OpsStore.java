package ua.vsevolod.lobby.feature.admin;

import ua.vsevolod.lobby.config.LobbyConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class OpsStore {

    private static final Path FILE = Paths.get("storage", "ops.txt");

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

    public static void save() {
        List<String> snapshot;
        synchronized (OpsStore.class) {
            snapshot = List.copyOf(LobbyConfig.Settings.BYPASS_USERS);
        }
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
