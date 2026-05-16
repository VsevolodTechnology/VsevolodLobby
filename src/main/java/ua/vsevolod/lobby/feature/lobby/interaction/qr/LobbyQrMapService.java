package ua.vsevolod.lobby.feature.lobby.interaction.qr;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.MapDataPacket;

public final class LobbyQrMapService {
    private static final MapDataPacket PACKET =
            LobbyQrMapRenderer.createPacket(LobbyQrMapItem.MAP_ID, LobbyQrMapItem.URL);

    private LobbyQrMapService() {
    }

    /**
     * Force the class init (and therefore the {@link #PACKET} static-block QR render) to happen
     * at server startup instead of on the first join. The QR pipeline runs ZXing + Graphics2D —
     * tens of milliseconds; not catastrophic on the tick thread, but worth lifting out of the
     * critical-path join sequence. Call once from bootstrap.
     */
    public static void preinit() {
        // Touching PACKET via a no-op assertion forces <clinit> if it hasn't run yet.
        assert PACKET != null;
    }

    public static void give(Player player) {
        player.getInventory().setEquipment(EquipmentSlot.OFF_HAND, (byte) 1, LobbyQrMapItem.create());
        player.sendPacket(PACKET);
    }

    public static void refresh(Player player) {
        player.sendPacket(PACKET);
    }
}
