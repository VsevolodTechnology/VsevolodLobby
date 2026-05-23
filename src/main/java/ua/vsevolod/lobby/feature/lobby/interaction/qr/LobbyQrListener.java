package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-/left-clicking the off-hand QR card shows the configurable socials message.
 * Layout lives in {@link SocialCardRenderer} so other surfaces (menu items via the
 * {@code [socials]} action) render an identical card.
 */
public class LobbyQrListener implements LobbyEventRegistration {

    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    @Override
    public void register(GlobalEventHandler handler) {
        handler.addListener(PlayerUseItemEvent.class, event -> {
            Player p = event.getPlayer();
            if (p.getItemInMainHand().material() != Material.AIR) return;
            if (!isQrItem(event.getItemStack())) return;
            tryShowSocials(p);
        });

        handler.addListener(PlayerHandAnimationEvent.class, event -> {
            if (event.getHand() != PlayerHand.MAIN) return;
            Player p = event.getPlayer();
            if (p.getItemInMainHand().material() != Material.AIR) return;
            if (!isQrItem(p.getItemInOffHand())) return;
            tryShowSocials(p);
        });

        handler.addListener(PlayerDisconnectEvent.class, event ->
                lastUse.remove(event.getPlayer().getUuid()));
    }

    private static boolean isQrItem(ItemStack item) {
        return item.material() == Material.FILLED_MAP && item.hasTag(LobbyQrMapItem.QR_TAG);
    }

    private void tryShowSocials(Player p) {
        QrCardConfig cfg = QrCardConfig.get();
        long now = System.currentTimeMillis();
        Long last = lastUse.put(p.getUuid(), now);
        if (last != null && (now - last) < cfg.cooldownMs) return;
        p.sendMessage(SocialCardRenderer.render(null));
    }
}
