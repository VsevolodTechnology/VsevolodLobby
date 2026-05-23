package ua.vsevolod.lobby.feature.lobby.player.chat;

import ua.vsevolod.lobby.util.WriteBehindCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent chat lock state.
 * Stored in {@code storage/chat-state.txt} so it survives server restarts.
 *
 * <p>Writes are buffered through {@link WriteBehindCache} with a 3 s debounce — the file
 * write happens off the tick thread on a virtual thread. Toggling the lock from chat commands
 * no longer stalls the game loop.</p>
 */
public final class ChatState {

    private static final Path FILE = Paths.get("storage", "chat-state.txt");
    private static final String KEY = "state";

    private static final AtomicBoolean LOCKED = new AtomicBoolean(false);

    /** Single-key write-behind cache. Key is {@code "state"}, value is the boolean as-is. */
    private static final WriteBehindCache<String, Boolean> WRITER =
            new WriteBehindCache<>("ChatState", Duration.ofSeconds(3), ChatState::writeToDisk);

    private ChatState() {}

    /** Called once at startup to restore the last saved state. */
    public static void load() {
        try {
            if (!Files.exists(FILE)) return;
            String content = Files.readString(FILE).trim();
            LOCKED.set("locked".equalsIgnoreCase(content));
        } catch (IOException e) {
            System.err.println("[ChatState] Failed to load: " + e.getMessage());
        }
    }

    public static boolean isLocked() {
        return LOCKED.get();
    }

    public static void setLocked(boolean value) {
        LOCKED.set(value);
        WRITER.put(KEY, value);
    }

    /** Shutdown hook: synchronously flush pending writes. */
    public static void flushAll() {
        WRITER.flushAll();
    }

    private static void writeToDisk(String ignored, Boolean locked) {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, locked ? "locked" : "unlocked");
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("[ChatState] Failed to save: " + e.getMessage());
        }
    }
}
