package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.client.handshake.ClientHandshakePacket;
import net.minestom.server.network.player.PlayerSocketConnection;

import net.kyori.adventure.text.Component;

/**
 * Replaces Minestom's built-in HandshakeListener which strictly rejects any
 * client whose protocol version != server's native (kicks "Invalid Version, please use 1.21.11").
 *
 * <p>When this server runs behind Velocity + ViaVersion / ViaBackwards the handshake reaches
 * Minestom with the client's ORIGINAL protocol number — Via translates the packet bodies but
 * does NOT rewrite the handshake protocol. This listener accepts every protocol version, so
 * Via can do its job downstream.</p>
 */
public final class HandshakeOverride {

    private static final Component TRANSFERS_DISABLED =
            Component.translatable("multiplayer.disconnect.transfers_disabled");

    private HandshakeOverride() {}

    public static void install() {
        MinecraftServer.getPacketListenerManager().setListener(
                ConnectionState.HANDSHAKE,
                ClientHandshakePacket.class,
                HandshakeOverride::handle
        );
    }

    private static void handle(ClientHandshakePacket packet, net.minestom.server.network.player.PlayerConnection connection) {
        final String address = packet.serverAddress();
        switch (packet.intent()) {
            case TRANSFER:
                connection.markTransferred(true);
                if (!ServerFlag.ACCEPT_TRANSFERS) {
                    connection.kick(TRANSFERS_DISABLED);
                    return;
                }
            case LOGIN:
            case STATUS:
            default:
                break;
        }

        if (connection instanceof PlayerSocketConnection socketConnection) {
            socketConnection.refreshServerInformation(address, packet.serverPort(), packet.protocolVersion());
        }
    }
}
