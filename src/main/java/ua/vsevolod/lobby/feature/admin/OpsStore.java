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

            // Atomic write: tmp file + Files.move. The previous Files.write with TRUNCATE_EXISTING
            // is NOT atomic — a JVM crash between TRUNCATE and the last name's flush would leave
            // the file partially populated and the next /reload would silently drop ops from the
            // set. The tmp+move pattern means readers either see the previous version or the new
            // version; never a torn half-write.
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.write(tmp, LobbyConfig.Settings.BYPASS_USERS, StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[OpsStore] Failed to save ops: " + e.getMessage());
        }
    }
}
