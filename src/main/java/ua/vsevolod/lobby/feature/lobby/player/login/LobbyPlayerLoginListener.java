package ua.vsevolod.lobby.feature.lobby.player.login;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public class LobbyPlayerLoginListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            if (LobbyConfig.Settings.SHUTTING_DOWN) {
                event.getConnection().kick(Component.text(LobbyConfig.Messages.SHUTTING_DOWN_MSG, NamedTextColor.RED));
            }
        });
    }
}


