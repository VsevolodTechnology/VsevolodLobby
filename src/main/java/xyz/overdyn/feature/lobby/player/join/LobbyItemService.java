package xyz.overdyn.feature.lobby.player.join;

import net.minestom.server.entity.Player;
import xyz.overdyn.feature.lobby.audio.music.LobbyMusicManager;
import xyz.overdyn.feature.lobby.interaction.qr.LobbyQrMapService;
import xyz.overdyn.feature.lobby.ui.menu.LobbyModeMenuItem;

public final class LobbyItemService {

    public void giveJoinItems(Player player, boolean musicEnabled) {
        player.getInventory().setItemStack(LobbyModeMenuItem.HOTBAR_SLOT, LobbyModeMenuItem.create());
        player.getInventory().setItemStack(8, LobbyMusicManager.getMusicToggle(musicEnabled));
        LobbyQrMapService.give(player);
    }
}
