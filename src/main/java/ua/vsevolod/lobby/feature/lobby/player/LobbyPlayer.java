package ua.vsevolod.lobby.feature.lobby.player;

import net.minestom.server.entity.Player;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;

public final class LobbyPlayer extends Player {

    private final Integer clientProtocol;

    public LobbyPlayer(PlayerConnection connection, GameProfile gameProfile, Integer clientProtocol) {
        super(connection, gameProfile);
        this.clientProtocol = clientProtocol;
    }

    public Integer getClientProtocol() {
        return clientProtocol;
    }
}