package xyz.overdyn.feature.lobby.ui.menu;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

public final class LobbyModeMenuItemListener implements LobbyEventRegistration {

    private final LobbyModeSelectorMenu menu;

    public LobbyModeMenuItemListener(LobbyModeSelectorMenu menu) {
        this.menu = menu;
    }

    @Override
    public void register(GlobalEventHandler eventHandler) {
        eventHandler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getHand() != PlayerHand.MAIN) {
                return;
            }

            ItemStack item = event.getItemStack();
            if (!item.hasTag(LobbyModeMenuItem.MENU_ITEM_TAG)) {
                return;
            }

            event.setCancelled(true);
            event.getPlayer().openInventory(menu.getMenu());
        });
    }
}
