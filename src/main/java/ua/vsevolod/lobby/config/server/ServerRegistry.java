package ua.vsevolod.lobby.config.server;

import java.util.List;
import java.util.Optional;

/**
 * Thin facade over {@link ServersConfig} — the registry is now config-driven
 * ({@code config/servers.yml}), not hardcoded. Always reflects the latest reload.
 */
public final class ServerRegistry {

    private ServerRegistry() {
    }

    /** All configured servers, in config order. */
    public static List<ServerInfo> servers() {
        return ServersConfig.get().serverInfos();
    }

    public static Optional<ServerInfo> findById(String id) {
        return ServersConfig.get().findById(id);
    }
}
