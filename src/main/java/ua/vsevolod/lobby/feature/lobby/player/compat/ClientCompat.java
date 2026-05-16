package ua.vsevolod.lobby.feature.lobby.player.compat;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;

/**
 * Resolves a {@link Player} to their original client protocol version and answers feature-support
 * questions via {@link ClientCapabilities}.
 *
 * <p>Protocol lookup order:</p>
 * <ol>
 *   <li>{@link LobbyConfig.Settings#IDENTIFIER_CLIENT_PROTOCOL} tag — set by
 *       {@code LobbyPlayerProvider} from the {@code vsevolod_lobby_protocol} GameProfile
 *       property that the upstream Velocity plugin forwards. This is the most accurate value
 *       because it reflects the ORIGINAL client protocol before any proxy translation.</li>
 *   <li>{@code player.getPlayerConnection().getProtocolVersion()} — falls back to the handshake
 *       value when no proxy property was forwarded.</li>
 *   <li>{@code MinecraftServer.PROTOCOL_VERSION} — last-resort default; treats the player as
 *       native-version so we don't accidentally degrade for unknown clients.</li>
 * </ol>
 */
public final class ClientCompat {

    private ClientCompat() {}

    public static int protocolOf(Player player) {
        Integer tagged = player.getTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL);
        if (tagged != null && tagged > 0) {
            return tagged;
        }
        int handshake = player.getPlayerConnection().getProtocolVersion();
        if (handshake > 0) {
            return handshake;
        }
        return MinecraftServer.PROTOCOL_VERSION;
    }

    public static boolean supportsTextDisplay(Player player) {
        return ClientCapabilities.supportsTextDisplay(protocolOf(player));
    }

    public static boolean supportsTransfer(Player player) {
        return ClientCapabilities.supportsTransfer(protocolOf(player));
    }

    public static boolean supportsConfigurationPhase(Player player) {
        return ClientCapabilities.supportsConfigurationPhase(protocolOf(player));
    }
}
