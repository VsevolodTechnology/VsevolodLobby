package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerSpawnEvent;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public final class LobbyJoinListener implements LobbyEventRegistration {

    private final LobbyJoinInitializer initializer;

    public LobbyJoinListener(LobbyJoinInitializer initializer) {
        this.initializer = initializer;
    }

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                initializer.initialize(event.getPlayer());
            }
        });
    }
}

