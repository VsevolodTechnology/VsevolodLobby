package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.server.ServerInfo;
import ua.vsevolod.lobby.config.server.ServerRegistry;
import ua.vsevolod.lobby.config.server.ServersConfig;
import ua.vsevolod.lobby.util.Text;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Single entry point for sending a player to another server. Used by the {@code [connect]}
 * command prefix, the config-driven menus, and the hardcoded mode selector — so the
 * status gating and feedback messages stay consistent everywhere.
 */
public final class ServerConnector {

    private static final String CHANNEL = "bungeecord:main";

    private ServerConnector() {
    }

    /**
     * Connect {@code player} to {@code server}, honouring its {@link ServerInfo#effectiveStatus()}.
     * A non-joinable server only shows the configured feedback message.
     */
    public static void connect(Player player, ServerInfo server) {
        ServersConfig cfg = ServersConfig.get();
        player.closeInventory();

        switch (server.effectiveStatus()) {
            case ONLINE -> {
                message(player, cfg.connectOnline, server);
                transfer(player, server.id());
            }
            case OFFLINE -> message(player, cfg.connectOffline, server);
            case LOADING -> message(player, cfg.connectLoading, server);
            case SOON -> message(player, cfg.connectSoon, server);
        }
    }

    /** Connect by id; an unknown id falls back to a raw transfer (no gating). */
    public static void connect(Player player, String serverId) {
        ServerRegistry.findById(serverId).ifPresentOrElse(
                server -> connect(player, server),
                () -> {
                    player.closeInventory();
                    transfer(player, serverId);
                });
    }

    private static void message(Player player, String body, ServerInfo server) {
        ServersConfig cfg = ServersConfig.get();
        String prefix = cfg.connectPrefix == null ? "" : cfg.connectPrefix;
        String filled = body
                .replace("{server}", server.id())
                .replace("{world}", server.worldName());
        player.sendMessage(Text.raw(prefix + filled));
    }

    /** Raw BungeeCord {@code Connect} plugin message — no status check. */
    public static void transfer(Player player, String serverId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(serverId);
            player.sendPluginMessage(CHANNEL, bytes.toByteArray());
        } catch (IOException e) {
            System.err.println("[ServerConnector] transfer " + player.getUsername()
                    + " -> " + serverId + " failed: " + e.getMessage());
        }
    }
}
