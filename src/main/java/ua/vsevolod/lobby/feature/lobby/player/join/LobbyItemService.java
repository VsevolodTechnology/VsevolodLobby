package ua.vsevolod.lobby.feature.lobby.player.join;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.LobbyQrMapService;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.LobbySettingsMenu;

public final class LobbyItemService {

    public void giveJoinItems(Player player) {
        JoinItemManager.giveAll(player);
        player.getInventory().setItemStack(LobbySettingsMenu.HOTBAR_SLOT, LobbySettingsMenu.createHotbarItem());
        LobbyQrMapService.give(player);
    }
}
