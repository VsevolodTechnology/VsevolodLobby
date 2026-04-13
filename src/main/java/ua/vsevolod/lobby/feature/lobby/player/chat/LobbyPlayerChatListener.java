package ua.vsevolod.lobby.feature.lobby.player.chat;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerChatEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Text;

public class LobbyPlayerChatListener implements LobbyEventRegistration {
    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerChatEvent.class, event -> {
            var player = event.getPlayer();
            event.setCancelled(true);
            if (!LobbyConfig.Settings.BYPASS_USERS.contains(player.getUsername()))
                return;

            var message = Text.c(LobbyConfig.Project.WHITE_COLOR_ORIGINAL +player.getUsername())
                    .append(Component.space()).append(Component.text(">"))
                    .append(Component.space()).append(Text.c(event.getRawMessage()));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(e -> e.sendMessage(message));
        });
    }
}


