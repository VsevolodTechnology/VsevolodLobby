package ua.vsevolod.lobby.feature.lobby.interaction.npc;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcAction;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Dispatches {@link NpcAction} instances to runtime handlers.
 *
 * <p>Handlers are registered at bootstrap by feature modules that have the live services
 * (mode-selector menu, parkour service, …). Unknown action types are silently no-op
 * — a startup log warning is emitted once on registration so admins can spot typos.</p>
 */
public final class NpcActionExecutor {

    public interface Handler {
        void run(Player player, NpcAction action);
    }

    private final java.util.Map<String, Handler> handlers = new java.util.concurrent.ConcurrentHashMap<>();

    public NpcActionExecutor() {
        register("none", (player, action) -> { /* no-op */ });
        register("run-command", NpcActionExecutor::runCommand);
    }

    public void register(String type, Handler handler) {
        handlers.put(type.toLowerCase(), handler);
    }

    /**
     * Convenience for actions like {@code parkour-start}, {@code open-menu} that just need a
     * Player-Consumer wired up from the calling site without caring about the action's target.
     */
    public void registerSimple(String type, Consumer<Player> handler) {
        register(type, (player, action) -> handler.accept(player));
    }

    /**
     * Convenience for handlers that resolve {@code action.target} to a runtime object
     * (e.g. menu by id) and then act on it. Returns {@code null} from {@code resolver}
     * to drop the action silently.
     */
    public <T> void registerKeyed(String type, Function<String, T> resolver, java.util.function.BiConsumer<Player, T> handler) {
        register(type, (player, action) -> {
            T value = resolver.apply(action.target());
            if (value != null) handler.accept(player, value);
        });
    }

    public void execute(Player player, NpcAction action) {
        if (action == null || action.isNone()) return;
        Handler handler = handlers.get(action.type().toLowerCase());
        if (handler == null) return;
        handler.run(player, action);
    }

    private static void runCommand(Player player, NpcAction action) {
        String cmd = action.target();
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        if (action.executeAsOp()) {
            int previousLevel = player.getPermissionLevel();
            try {
                player.setPermissionLevel(4);
                MinecraftServer.getCommandManager().execute(player, trimmed);
            } finally {
                player.setPermissionLevel(previousLevel);
            }
        } else {
            MinecraftServer.getCommandManager().execute(player, trimmed);
        }
    }
}
