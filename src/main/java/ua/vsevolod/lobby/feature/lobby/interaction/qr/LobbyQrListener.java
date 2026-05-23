package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;
import ua.vsevolod.lobby.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Right-/left-clicking the off-hand QR card shows the configurable socials message.
 * All text and links come from {@code config/qr-card.yml} ({@link QrCardConfig}) — the
 * message is rebuilt from the live config snapshot on every show, so {@code /reload} takes
 * effect immediately.
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
        p.sendMessage(buildSocialsMessage(cfg));
    }

    private static Component buildSocialsMessage(QrCardConfig cfg) {
        Component message = Component.newline()
                .append(Text.raw(cfg.header).decoration(TextDecoration.ITALIC, false))
                .append(Component.newline());
        for (QrCardConfig.SocialLink link : cfg.links) {
            message = message.append(socialLine(link, cfg.linkFormat, cfg.hoverText))
                    .append(Component.newline());
        }
        return message;
    }

    private static Component socialLine(QrCardConfig.SocialLink link, String linkFormat, String hoverText) {
        // The visible line is fully driven by linkFormat (colours included). The bare url is
        // used verbatim for the click action so colour codes never leak into the URL.
        String rendered = linkFormat
                .replace("{color}", link.color())
                .replace("{label}", link.label())
                .replace("{hint}", link.hint())
                .replace("{url}", link.url());
        String clickUrl = link.url().startsWith("http") ? link.url() : "https://" + link.url();

        return Text.raw(rendered)
                .clickEvent(ClickEvent.openUrl(clickUrl))
                .hoverEvent(HoverEvent.showText(Text.raw(hoverText)))
                .decoration(TextDecoration.ITALIC, false);
    }
}
