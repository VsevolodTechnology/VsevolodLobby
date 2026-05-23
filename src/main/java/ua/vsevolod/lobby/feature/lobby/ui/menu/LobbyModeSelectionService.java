package ua.vsevolod.lobby.feature.lobby.ui.menu;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.server.ServerInfo;

/**
 * Handles a server pick from the hardcoded {@link LobbyModeSelectorMenu}. Delegates to
 * {@link ServerConnector} so status gating and feedback messages match the config-driven
 * menus and the {@code [connect]} command.
 */
public final class LobbyModeSelectionService {

    public void selectServer(Player player, ServerInfo server) {
        ServerConnector.connect(player, server);
    }
}
