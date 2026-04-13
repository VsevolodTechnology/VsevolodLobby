package ua.vsevolod.lobby.feature.lobby.player;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionManager;
import net.minestom.server.network.PlayerProvider;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.jspecify.annotations.NonNull;
import ua.vsevolod.lobby.config.LobbyConfig;

public final class LobbyPlayerProvider implements PlayerProvider {

    @Override
    public @NonNull Player createPlayer(@NonNull PlayerConnection connection, @NonNull GameProfile gameProfile) {
        Player player = new Player(connection, gameProfile);

        for (GameProfile.Property property : gameProfile.properties()) {
            if (property.name().equals(LobbyConfig.Settings.IDENTIFIER_VELOCITY_MESSAGE)) {
                try {
                    int protocol = Integer.parseInt(property.value());
                    player.setTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL, protocol);
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }

        return player;
    }

    public static void register() {
        ConnectionManager connectionManager = MinecraftServer.getConnectionManager();
        connectionManager.setPlayerProvider(new LobbyPlayerProvider());
    }
}