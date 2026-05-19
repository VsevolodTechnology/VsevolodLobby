package ua.vsevolod.lobby.feature.lobby.player.inventory;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.inventory.PlayerInventory;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public class PlayerInventoryLockListener implements LobbyEventRegistration {
    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(InventoryPreClickEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;

            if (event.getInventory() instanceof PlayerInventory) {
                event.setCancelled(true);
            }
        });
        handler.addListener(CreativeInventoryActionEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;

            event.setCancelled(true);
        });
        handler.addListener(PlayerSwapItemEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;

            event.setCancelled(true);
        });
        handler.addListener(ItemDropEvent.class, event -> {
            event.setCancelled(true);
        });
    }
}


