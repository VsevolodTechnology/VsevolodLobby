package xyz.overdyn.feature.lobby.interaction.qr;

import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.MapDataPacket;

public final class LobbyQrMapService {
    private static final MapDataPacket PACKET =
            LobbyQrMapRenderer.createPacket(LobbyQrMapItem.MAP_ID, LobbyQrMapItem.URL);

    private LobbyQrMapService() {
    }

    public static void give(Player player) {
        player.getInventory().setEquipment(EquipmentSlot.OFF_HAND, (byte) 1, LobbyQrMapItem.create());
        player.sendPacket(PACKET);
    }

    public static void refresh(Player player) {
        player.sendPacket(PACKET);
    }
}
