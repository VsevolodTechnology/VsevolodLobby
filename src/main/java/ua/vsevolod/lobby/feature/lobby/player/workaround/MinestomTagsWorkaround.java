package ua.vsevolod.lobby.feature.lobby.player.workaround;

import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.network.packet.server.common.TagsPacket;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.bootstrap.LobbyEventRegistration;

public class MinestomTagsWorkaround implements LobbyEventRegistration {  //TODO is temp Пробка для попа

    @Override
    public void register(GlobalEventHandler events) { //TODO is temp Пробка для попа
        if (!LobbyConfig.Settings.ENABLE_PROTOCOL_WORKAROUND) return;
        events.addListener(PlayerPacketOutEvent.class, e -> {
            if (!(e.getPacket() instanceof TagsPacket)) return;

            if (!e.getPlayer().hasTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL)) return;

            var protocol = e.getPlayer().getTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL);

            if (LobbyConfig.Settings.PROTOCOLS_WITH_WORKAROUNDS.contains(protocol)) {
                e.setCancelled(true);
            }
        });
    }
}