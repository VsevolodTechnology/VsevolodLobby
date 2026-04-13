package ua.vsevolod.lobby.feature.lobby.bootstrap;

import net.minestom.server.event.GlobalEventHandler;

@FunctionalInterface
public interface LobbyEventRegistration {

    void register(GlobalEventHandler eventHandler);
}
