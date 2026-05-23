package ua.vsevolod.lobby.feature.lobby.player.login;

import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerPreLoginEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Text;

public class LobbyPlayerLoginListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(AsyncPlayerPreLoginEvent.class, event -> {
            if (LobbyConfig.Settings.SHUTTING_DOWN) {
                event.getConnection().kick(Text.raw(LobbyConfig.Messages.SHUTTING_DOWN_MSG));
                return;
            }
            int online = MinecraftServer.getConnectionManager().getOnlinePlayerCount();
            if (online >= LobbyConfig.Settings.MAX_PLAYERS
                    && !LobbyConfig.Settings.BYPASS_USERS.contains(event.getGameProfile().name())) {
                event.getConnection().kick(Text.raw("<red>Сервер заполнен! Попробуй позже."));
            }
        });
    }
}
