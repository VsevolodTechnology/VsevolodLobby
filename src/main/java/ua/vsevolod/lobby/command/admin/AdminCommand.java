package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

/**
 * Base class for operator-only commands.
 *
 * <h3>What it gives you</h3>
 * <ul>
 *   <li><b>One permission check, one source of truth.</b> The condition is wired up to
 *       {@link LobbyConfig.Settings#BYPASS_USERS} so subclasses don't reinvent the test.
 *       Audit HIGH-priority finding: 13 admin commands each duplicated the check twice
 *       (once in {@code setCondition}, again at the top of every executor branch).</li>
 *   <li><b>Auto-registration.</b> The constructor calls
 *       {@code MinecraftServer.getCommandManager().register(this)} — subclasses don't.</li>
 *   <li><b>Console opt-in.</b> Commands that should be runnable from the server console
 *       (e.g. {@code /stop}, {@code /restart}) construct with {@code allowsConsole = true}.</li>
 * </ul>
 *
 * <h3>What you still need to do</h3>
 * <p>Subclasses install their executor + syntaxes normally. They can safely assume the
 * permission gate has fired — but should still pattern-match {@code sender instanceof Player p}
 * to get a typed reference, just as before.</p>
 */
public abstract class AdminCommand extends Command {

    protected AdminCommand(String name, String... aliases) {
        this(name, false, aliases);
    }

    protected AdminCommand(String name, boolean allowsConsole, String... aliases) {
        super(name, aliases);
        setCondition(allowsConsole
                ? (sender, _cs) -> isAdminOrConsole(sender)
                : (sender, _cs) -> isAdmin(sender));
        MinecraftServer.getCommandManager().register(this);
    }

    public static boolean isAdmin(CommandSender sender) {
        return sender instanceof Player p
                && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername());
    }

    public static boolean isAdminOrConsole(CommandSender sender) {
        return sender instanceof ConsoleSender || isAdmin(sender);
    }
}
