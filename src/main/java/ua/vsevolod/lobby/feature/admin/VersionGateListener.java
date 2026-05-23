package ua.vsevolod.lobby.feature.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import ua.vsevolod.lobby.config.LobbyConfig;

public final class VersionGateListener {

    private static final TextColor PROJECT_WHITE = LobbyConfig.Project.WHITE_COLOR_TEXT_ORIGINAL;
    private static final TextColor MUTED = NamedTextColor.GRAY;

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

            Component msg = Component.text("Ваша версия Minecraft не поддерживается.\n", NamedTextColor.RED)
                    .append(Component.text("Протокол: ", MUTED))
                    .append(Component.text(protocol + "\n", PROJECT_WHITE))
                    .append(Component.text("Допустимо: ", MUTED))
                    .append(Component.text(VersionGate.getMin() + " — " + VersionGate.getMax(), PROJECT_WHITE));
            player.kick(msg);
        });
    }
}
