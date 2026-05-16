package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrMapService;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemManager;

public final class LobbyItemService {

    public void giveJoinItems(Player player, boolean musicEnabled) {
        // Configurable items (mode-selector compass and anything else added in config/join-items.yml).
        JoinItemManager.giveAll(player);

        // Music toggle keeps its own state-driven handler — not yet config-driven.
        player.getInventory().setItemStack(8, LobbyMusicManager.getMusicToggle(musicEnabled));

        // QR map is a special texture item with its own listener.
        LobbyQrMapService.give(player);
    }
}
