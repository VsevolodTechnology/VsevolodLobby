package ua.vsevolod.lobby.command.admin;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.player.chat.ChatState;
import ua.vsevolod.lobby.feature.lobby.player.chat.LobbyPlayerChatListener;
import ua.vsevolod.lobby.util.ServerLogger;

/**
 * /chat lock [-s]   — locks the chat (prevents players from sending messages)
 * /chat unlock [-s] — unlocks the chat
 *
 * The -s (silent) flag suppresses the server-wide announcement.
 * Only admins (BYPASS_USERS) and the console can use this command.
 */
public class ChatCommand extends AdminCommand {

    private static final Component CHAT_PREFIX = LobbyPlayerChatListener.CHAT_PREFIX;

    private static final Component MSG_USAGE = CHAT_PREFIX
            .append(Component.text("Использование: ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            .append(Component.text("/chat lock|unlock [-s]", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));

    private static final Component MSG_ALREADY_LOCKED = CHAT_PREFIX
            .append(Component.text("Чат уже заблокирован.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    private static final Component MSG_ALREADY_UNLOCKED = CHAT_PREFIX
            .append(Component.text("Чат уже разблокирован.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    private static final Component MSG_LOCKED_SILENT = CHAT_PREFIX
            .append(Component.text("Чат заблокирован ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            .append(Component.text("(тихо).", TextColor.color(0xB0A89E)));
    private static final Component MSG_UNLOCKED_SILENT = CHAT_PREFIX
            .append(Component.text("Чат разблокирован ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
            .append(Component.text("(тихо).", TextColor.color(0xB0A89E)));

    private static final Component BROADCAST_LOCKED = CHAT_PREFIX
            .append(Component.text("✖ ", TextColor.color(0xE36666)))
            .append(Component.text("Чат заблокирован администрацией.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));
    private static final Component BROADCAST_UNLOCKED = CHAT_PREFIX
            .append(Component.text("✔ ", TextColor.color(0x81E366)))
            .append(Component.text("Чат разблокирован.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));

    private static final Sound SND_LOCK   = Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASS,  Sound.Source.MASTER, 1.0f, 0.5f);
    private static final Sound SND_UNLOCK = Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1.0f, 1.5f);

    public ChatCommand() {
        super("chat", true);   // allowsConsole=true — /chat lock|unlock works from server console

        var actionArg = new ArgumentString("action");
        var flagArg   = new ArgumentString("flag");

        actionArg.setSuggestionCallback((sender, ctx, suggestion) -> {
            suggestion.addEntry(new SuggestionEntry("lock"));
            suggestion.addEntry(new SuggestionEntry("unlock"));
        });
        flagArg.setSuggestionCallback((sender, ctx, suggestion) ->
                suggestion.addEntry(new SuggestionEntry("-s")));

        setDefaultExecutor((sender, ctx) -> sender.sendMessage(MSG_USAGE));

        addSyntax((sender, ctx) -> handle(sender, ctx.get(actionArg), null),
                actionArg);
        addSyntax((sender, ctx) -> handle(sender, ctx.get(actionArg), ctx.get(flagArg)),
                actionArg, flagArg);
    }

    private static void handle(CommandSender sender, String action, String flag) {
        boolean silent = "-s".equalsIgnoreCase(flag);

        switch (action.toLowerCase()) {
            case "lock" -> {
                if (ChatState.isLocked()) {
                    sender.sendMessage(MSG_ALREADY_LOCKED);
                    return;
                }
                ChatState.setLocked(true);
                ServerLogger.get().info("Chat locked by " + senderName(sender));
                if (silent) {
                    sender.sendMessage(MSG_LOCKED_SILENT);
                } else {
                    broadcastWithSound(BROADCAST_LOCKED, SND_LOCK);
                }
            }
            case "unlock" -> {
                if (!ChatState.isLocked()) {
                    sender.sendMessage(MSG_ALREADY_UNLOCKED);
                    return;
                }
                ChatState.setLocked(false);
                ServerLogger.get().info("Chat unlocked by " + senderName(sender));
                if (silent) {
                    sender.sendMessage(MSG_UNLOCKED_SILENT);
                } else {
                    broadcastWithSound(BROADCAST_UNLOCKED, SND_UNLOCK);
                }
            }
            default -> sender.sendMessage(MSG_USAGE);
        }
    }

    private static void broadcastWithSound(Component msg, Sound sound) {
        MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(p -> {
            p.sendMessage(msg);
            p.playSound(sound);
        });
    }

    private static String senderName(CommandSender sender) {
        return sender instanceof Player p ? p.getUsername() : "Console";
    }
}
