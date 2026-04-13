package ua.vsevolod.lobby.feature.lobby.world.protection;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public final class LobbyBlockProtectionListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;
            event.setCancelled(true);
        });
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;
            event.setCancelled(true);
        });
    }
}

