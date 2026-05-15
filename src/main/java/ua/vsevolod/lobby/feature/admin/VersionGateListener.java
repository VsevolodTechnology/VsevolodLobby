package ua.vsevolod.lobby.feature.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import ua.vsevolod.lobby.config.LobbyConfig;

public final class VersionGateListener {

    private VersionGateListener() {}

    public static void register(GlobalEventHandler events) {
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            if (!VersionGate.isEnabled()) return;

            var player = event.getPlayer();
            Integer tagged = player.getTag(LobbyConfig.Settings.IDENTIFIER_CLIENT_PROTOCOL);
            int protocol = (tagged != null && tagged > 0)
                    ? tagged
                    : player.getPlayerConnection().getProtocolVersion();

            if (VersionGate.allows(protocol)) return;

            String msg = "§cВаша версия Minecraft не поддерживается.\n"
                    + "§7Протокол: §f" + protocol + "\n"
                    + "§7Допустимо: §f" + VersionGate.getMin() + " — " + VersionGate.getMax();
            player.kick(Component.text(msg, NamedTextColor.RED));
        });
    }
}
