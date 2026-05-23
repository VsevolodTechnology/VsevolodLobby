package ua.vsevolod.lobby.feature.lobby.player.chat;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.sound.SoundEvent;
import ua.vsevolod.lobby.config.ChatConfig;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LobbyPlayerChatListener implements LobbyEventRegistration {

    // Project-style [Чат] prefix — consistent with [Паркур] and [Настройки]
    public static final Component CHAT_PREFIX = Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Text.c("<gradient:#AE3AF3:#985DBC><bold>Чат</bold></gradient>"))
            .append(Component.text("]", NamedTextColor.DARK_GRAY))
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.space());

    private static final Component MSG_LOCKED = CHAT_PREFIX
            .append(Component.text("Чат заблокирован администрацией.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL));

    private static final Sound SND_BLOCKED  = Sound.sound(SoundEvent.ENTITY_VILLAGER_NO,      Sound.Source.MASTER, 0.7f, 1.0f);
    private static final Sound SND_MENTION  = Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING,  Sound.Source.MASTER, 1.0f, 2.0f);

    // Matches @Nickname (valid Minecraft username chars, 1–16)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]{1,16})");

    // Strips legacy &X color codes and &#RRGGBB hex codes from player input
    private static final Pattern COLOR_STRIP = Pattern.compile(
            "(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]|§[0-9a-fk-or]");

    // Last message timestamp per player UUID (epoch ms)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public void register(GlobalEventHandler handler) {
        ChatFilter filter = buildFilter();

        handler.addListener(PlayerChatEvent.class, event -> {
            event.setCancelled(true);
            var player = event.getPlayer();
            boolean isAdmin = LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername());

            String rawMessage = event.getRawMessage();

            if (!isAdmin) {
                // Chat locked
                if (ChatState.isLocked()) {
                    player.playSound(SND_BLOCKED);
                    player.sendMessage(MSG_LOCKED);
                    return;
                }

                ChatConfig cfg = ChatConfig.get();

                // Anti-spam cooldown
                if (cfg.cooldownSeconds > 0) {
                    long now = System.currentTimeMillis();
                    long cooldownMs = cfg.cooldownSeconds * 1000L;
                    long last = cooldowns.getOrDefault(player.getUuid(), 0L);
                    if (now - last < cooldownMs) {
                        long remaining = (cooldownMs - (now - last) + 999) / 1000;
                        player.playSound(SND_BLOCKED);
                        player.sendMessage(CHAT_PREFIX
                                .append(Component.text("Подождите ещё ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                                .append(Component.text(remaining + " сек", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                                .append(Component.text(", прежде чем писать снова.", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
                        return;
                    }
                    cooldowns.put(player.getUuid(), now);
                }

                // Strip color codes — non-admins cannot use color/formatting
                rawMessage = COLOR_STRIP.matcher(rawMessage).replaceAll("");

                // Message length (after stripping)
                if (cfg.maxLength > 0 && rawMessage.length() > cfg.maxLength) {
                    player.sendMessage(CHAT_PREFIX
                            .append(Component.text("Сообщение слишком длинное (макс. ", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                            .append(Component.text(cfg.maxLength + " символов", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                            .append(Component.text(").", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
                    return;
                }

                // Anti-advertising filter
                if (cfg.filterEnabled && filter.isBlocked(rawMessage)) {
                    player.sendMessage(CHAT_PREFIX.append(Text.raw(cfg.warnMessage)));
                    return;
                }
            }

            // Process @mentions and highlight them
            String processedMessage = processMentions(rawMessage, player);

            // Format and broadcast
            ChatConfig cfg = ChatConfig.get();
            String formatted = cfg.format
                    .replace("{player}", player.getUsername())
                    .replace("{message}", processedMessage);
            Component message = Text.raw(formatted);
            MinecraftServer.getConnectionManager().getOnlinePlayers()
                    .forEach(p -> p.sendMessage(message));
        });

        // Clean up cooldown entries on disconnect to avoid unbounded map growth
        handler.addListener(PlayerDisconnectEvent.class, event ->
                cooldowns.remove(event.getPlayer().getUuid()));
    }

    /**
     * Finds {@code @name} patterns in the message.
     * For each match that resolves to an online player, notifies them (sound + actionbar)
     * and highlights the mention in the message as yellow text.
     */
    private static String processMentions(String message, Player sender) {
        Matcher m = MENTION_PATTERN.matcher(message);
        if (!m.find()) return message;
        m.reset();

        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(1);
            Player mentioned = findPlayer(name);
            if (mentioned != null && mentioned != sender) {
                notifyMentioned(mentioned, sender.getUsername());
                // <#C58AF0> = yellow highlight, <gray> = back to gray after
                m.appendReplacement(sb, Matcher.quoteReplacement("<#C58AF0>@" + mentioned.getUsername() + "<gray>"));
            } else {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Player findPlayer(String name) {
        // Use the connection manager's built-in lookup — Minestom maintains a username index
        // internally, so this is O(1). The previous loop walked every online player on every
        // @mention. Audit MED-06.
        return MinecraftServer.getConnectionManager().getOnlinePlayerByUsername(name);
    }

    private static void notifyMentioned(Player mentioned, String fromName) {
        mentioned.playSound(SND_MENTION);
        mentioned.sendActionBar(CHAT_PREFIX
                .append(Component.text(fromName, LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL))
                .append(Component.text(" упомянул вас", LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL)));
    }

    private static ChatFilter buildFilter() {
        ChatConfig cfg = ChatConfig.get();
        return new ChatFilter(cfg.blockLinks, cfg.blockDomains, cfg.blockIps, cfg.blockObfuscated);
    }
}
