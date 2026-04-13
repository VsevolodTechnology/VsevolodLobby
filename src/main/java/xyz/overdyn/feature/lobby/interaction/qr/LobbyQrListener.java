package xyz.overdyn.feature.lobby.interaction.qr;

import net.kyori.adventure.text.Component;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import xyz.overdyn.feature.lobby.bootstrap.LobbyEventRegistration;

public class LobbyQrListener implements LobbyEventRegistration {
    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getPlayer().getItemInMainHand().material() != Material.AIR) return;
            ItemStack item = event.getItemStack();
            if (item.material() != Material.FILLED_MAP) return;
            if (!item.hasTag(LobbyQrMapItem.QR_TAG)) return;

            event.getPlayer().sendMessage(Component.text("§eQR: https://example.com"));
            event.getPlayer().sendMessage(Component.text("§7Тут дальше можно сделать реальную map-отрисовку QR."));
        });
    }
}


