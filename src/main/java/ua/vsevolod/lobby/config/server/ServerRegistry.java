package ua.vsevolod.lobby.config.server;

import net.minestom.server.item.Material;

import java.util.List;
import java.util.Optional;

public final class ServerRegistry {

    private ServerRegistry() {
    }

    public static final List<ServerInfo> LOBBY_SERVERS = List.of(
            new ServerInfo(
                    "grief.1.16x",
                    "Гриферский",
                    "1.21.8",
                    ServerStatus.ONLINE,
                    100,
                    new String[]{"ПвП", "Халява", "Freedom", "ТопСервер", "Кланы", "Рейды"},
                    Material.DIAMOND_SWORD
            ),
            new ServerInfo(
                    "anarchy.1.16x",
                    "Анархия",
                    "1.21.8",
                    ServerStatus.ONLINE,
                    100,
                    new String[]{"ПвП", "Халява", "Freedom", "ТопСервер", "Кланы", "Рейды"},
                    Material.DIAMOND_SWORD
            )
    );

    public static Optional<ServerInfo> findById(String id) {
        return LOBBY_SERVERS.stream()
                .filter(server -> server.id().equalsIgnoreCase(id))
                .findFirst();
    }
}
