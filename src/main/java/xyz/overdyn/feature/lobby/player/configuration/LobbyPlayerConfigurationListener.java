package xyz.overdyn.feature.lobby.player.configuration;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.Instance;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

public class LobbyPlayerConfigurationListener implements LobbyEventRegistration {

    private final Instance lobbyInstance;

    public LobbyPlayerConfigurationListener(Instance lobbyInstance) {
        this.lobbyInstance = lobbyInstance;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            var player = event.getPlayer();

            /*
             * Sets the instance (world) where the player will spawn when joining the server.
             */
            event.setSpawningInstance(lobbyInstance);

            /*
             * Sets the player's respawn point.
             * This location will be used when the player respawns or when the initial spawn is applied.
             */
            player.setRespawnPoint(LobbyConfig.Locations.SPAWN_POS_PLAYER);
        });
    }
}


