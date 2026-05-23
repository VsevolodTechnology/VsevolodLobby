package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.MapDataPacket;

public final class LobbyQrMapService {
    private static volatile MapDataPacket packet;

    private LobbyQrMapService() {
    }

    /**
     * Render the QR map texture from the configured {@code qr_url} at server startup, instead
     * of on the first join. The QR pipeline runs ZXing + Graphics2D — tens of milliseconds;
     * worth lifting out of the critical-path join sequence. Call once from bootstrap, after
     * the config manager has loaded {@code qr-card.yml}.
     *
     * <p>The texture is baked once here — changing {@code qr_url} therefore needs a restart,
     * not just {@code /reload} (the chat-message strings do hot-reload).</p>
     */
    public static void preinit() {
        QrCardConfig cfg = QrCardConfig.get();
        String qrUrl = ua.vsevolod.lobby.config.SocialsConfig.get().resolve(cfg.qrUrl);
        packet = LobbyQrMapRenderer.createPacket(
                LobbyQrMapItem.MAP_ID, qrUrl, cfg.imageFile);
    }

    public static void give(Player player) {
        player.getInventory().setEquipment(EquipmentSlot.OFF_HAND, (byte) 1, LobbyQrMapItem.create());
        if (packet != null) player.sendPacket(packet);
    }

    public static void refresh(Player player) {
        if (packet != null) player.sendPacket(packet);
    }
}
