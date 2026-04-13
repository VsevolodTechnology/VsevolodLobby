package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.audio.music.LobbyMusicManager;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrMapService;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbyModeMenuItem;

public final class LobbyItemService {

    public void giveJoinItems(Player player, boolean musicEnabled) {
        player.getInventory().setItemStack(LobbyModeMenuItem.HOTBAR_SLOT, LobbyModeMenuItem.create());
        player.getInventory().setItemStack(8, LobbyMusicManager.getMusicToggle(musicEnabled));
        LobbyQrMapService.give(player);
    }
}
