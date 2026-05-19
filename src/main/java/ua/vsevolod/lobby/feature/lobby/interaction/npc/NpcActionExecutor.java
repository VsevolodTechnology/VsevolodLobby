package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;
import ua.vsevolod.lobby.util.Text;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Executes DeluxeMenus-style command strings for players.
 *
 * <h3>Command prefixes:</h3>
 * <ul>
 *   <li>{@code [player] <cmd>}     — run {@code /cmd} as the player (multi-word supported)</li>
 *   <li>{@code [console] <cmd>}    — run {@code /cmd} as console (op-level)</li>
 *   <li>{@code [op] <cmd>}         — temporarily give op and run as player</li>
 *   <li>{@code [message] <text>}   — send a chat message to the player</li>
 *   <li>{@code [close]}            — close the player's open inventory</li>
 *   <li>{@code [connect] <server>} — transfer to another server</li>
 *   <li>{@code [menu] <id>}        — open a menu by id (wired externally)</li>
 *   <li>{@code [parkour]}          — start the parkour run (wired externally)</li>
 *   <li>{@code [broadcast] <text>} — broadcast message to all online players</li>
 * </ul>
 *
 * <p>Multi-word arguments are fully supported. Example:
 * {@code [player] server adventure} runs {@code /server adventure} as the player.</p>
 *
 * <p>For backwards compatibility, the old {@link #execute(Player, NpcAction)} API is
 * preserved so that join-items configs still work without migration.</p>
 */
public final class NpcActionExecutor {

    /** Handler for extensible prefixes registered at bootstrap (e.g. [menu], [connect]). */
    public interface Handler {
        void run(Player player, NpcAction action);
    }

    /** Handler for new [prefix] command strings. Receives player + the rest of the command string. */
    @FunctionalInterface
    public interface PrefixHandler {
        void run(Player player, String argument);
    }

    // --- Legacy action-type registry (used by JoinItems / old API) ---
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    // --- New prefix registry (used by NPCs / menus) ---
    private final Map<String, PrefixHandler> prefixHandlers = new ConcurrentHashMap<>();

    public NpcActionExecutor() {
        // Legacy: keep for join-items backwards compat
        register("none", (player, action) -> { /* no-op */ });
        register("run-command", NpcActionExecutor::runCommandLegacy);

        // New prefix handlers for built-in operations
        registerPrefix("player",    (player, cmd) -> runAsPlayer(player, cmd));
        registerPrefix("console",   (player, cmd) -> runAsConsole(player, cmd));
        registerPrefix("op",        (player, cmd) -> runAsOp(player, cmd));
        registerPrefix("message",   (player, text) -> player.sendMessage(Text.c(text)));
        registerPrefix("close",     (player, ignored) -> player.closeInventory());
        registerPrefix("broadcast", (player, text) -> {
            Component msg = Text.c(text);
            for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                p.sendMessage(msg);
            }
        });
        // [connect], [menu], [parkour] are wired in LobbyModule since they need live service refs
    }

    // ── Legacy API ────────────────────────────────────────────────────────────

    public void register(String type, Handler handler) {
        handlers.put(type.toLowerCase(), handler);
    }

    public void registerSimple(String type, java.util.function.Consumer<Player> handler) {
        register(type, (player, action) -> handler.accept(player));
    }

    public <T> void registerKeyed(String type, java.util.function.Function<String, T> resolver,
                                   java.util.function.BiConsumer<Player, T> handler) {
        register(type, (player, action) -> {
            T value = resolver.apply(action.target());
            if (value != null) handler.accept(player, value);
        });
    }

    /** Execute a legacy {@link NpcAction} (used by join-items). */
    public void execute(Player player, NpcAction action) {
        if (action == null || action.isNone()) return;
        Handler handler = handlers.get(action.type().toLowerCase());
        if (handler == null) return;
        handler.run(player, action);
    }

    // ── New [prefix] API ──────────────────────────────────────────────────────

    /**
     * Register a handler for a {@code [prefix]} command.
     * {@code argument} is the text after {@code [prefix] }, trimmed.
     * For prefix-only commands (like {@code [close]}) {@code argument} will be empty.
     */
    public void registerPrefix(String prefix, PrefixHandler handler) {
        prefixHandlers.put(prefix.toLowerCase(), handler);
    }

    /**
     * Execute a list of DeluxeMenus-style command strings for a player.
     * Each entry is one of: {@code [prefix] argument}, {@code [prefix]}, or a bare command.
     *
     * <p>Multi-word arguments are fully supported — everything after the closing {@code ]}
     * and a single space is the argument. Example: {@code [player] server adventure}
     * → runs {@code /server adventure} as the player.</p>
     */
    public void executeCommands(Player player, List<String> commands) {
        if (commands == null || commands.isEmpty()) return;
        for (String command : commands) {
            if (command == null || command.isBlank()) continue;
            try {
                executeOne(player, command.trim());
            } catch (Throwable t) {
                System.err.println("[NpcActionExecutor] Error executing command '" + command
                        + "' for " + player.getUsername() + ": " + t.getMessage());
            }
        }
    }

    private void executeOne(Player player, String command) {
        if (!command.startsWith("[")) {
            // No prefix — treat as bare player command
            runAsPlayer(player, command.startsWith("/") ? command.substring(1) : command);
            return;
        }

        int close = command.indexOf(']');
        if (close < 0) {
            // Malformed prefix — treat as message
            player.sendMessage(Text.c(command));
            return;
        }

        String prefix = command.substring(1, close).toLowerCase().trim();
        // Argument is everything after "] " (skip the space if present)
        String argument = close + 1 < command.length()
                ? command.substring(close + 1).replaceFirst("^ ", "")
                : "";

        PrefixHandler handler = prefixHandlers.get(prefix);
        if (handler == null) {
            System.err.println("[NpcActionExecutor] Unknown prefix '[" + prefix + "]' in command: " + command);
            return;
        }
        handler.run(player, argument);
    }

    // ── Built-in command runners ──────────────────────────────────────────────

    private static void runAsPlayer(Player player, String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        MinecraftServer.getCommandManager().execute(player, trimmed);
    }

    private static void runAsConsole(Player player, String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        // Run via the console sender (no player context)
        MinecraftServer.getCommandManager().execute(
                MinecraftServer.getCommandManager().getConsoleSender(), trimmed);
    }

    private static void runAsOp(Player player, String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        int prev = player.getPermissionLevel();
        try {
            player.setPermissionLevel(4);
            MinecraftServer.getCommandManager().execute(player, trimmed);
        } finally {
            player.setPermissionLevel(prev);
        }
    }

    // ── Legacy helper ────────────────────────────────────────────────────────

    private static void runCommandLegacy(Player player, NpcAction action) {
        String cmd = action.target();
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        if (action.executeAsOp()) {
            int prev = player.getPermissionLevel();
            try {
                player.setPermissionLevel(4);
                MinecraftServer.getCommandManager().execute(player, trimmed);
            } finally {
                player.setPermissionLevel(prev);
            }
        } else {
            MinecraftServer.getCommandManager().execute(player, trimmed);
        }
    }
}
