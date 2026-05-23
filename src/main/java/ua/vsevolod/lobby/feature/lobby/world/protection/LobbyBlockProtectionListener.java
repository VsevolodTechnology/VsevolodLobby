package ua.vsevolod.lobby.feature.lobby.world.protection;

import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.item.ItemStack;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemManager;

public final class LobbyBlockProtectionListener implements LobbyEventRegistration {

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerBlockBreakEvent.class, event -> {
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;
            event.setCancelled(true);
        });
        handler.addListener(PlayerBlockPlaceEvent.class, event -> {
            // Never let lobby UI items (compass, etc.) be placed — even by admins. The
            // JOIN_ITEM_ID tag is stamped by JoinItemManager onto every item it issues.
            PlayerHand hand = event.getHand();
            ItemStack stack = hand == PlayerHand.OFF
                    ? event.getPlayer().getItemInOffHand()
                    : event.getPlayer().getItemInMainHand();
            if (stack != null && stack.getTag(JoinItemManager.JOIN_ITEM_ID) != null) {
                event.setCancelled(true);
                return;
            }
            if (LobbyConfig.Settings.BYPASS_USERS.contains(event.getPlayer().getUsername())) return;
            event.setCancelled(true);
        });
    }
}

