package ua.vsevolod.lobby;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import com.viaversion.viaversion.api.Via;
import org.slf4j.Logger;

import java.util.List;

@Plugin(
        id = "vsevolod_lobby_version",
        name = "VsevolodLobbyVersion",
        version = "1.0.0",
        authors = {"t.me/overdynMC"}
)
public final class VelcoityPlugin {

    public static final String PROTOCOL_PROPERTY = "vsevolod_lobby_protocol";

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public VelcoityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onGameProfileRequest(PostLoginEvent event) {
        if (proxy.getPluginManager().getPlugin("viaversion").isEmpty()) {
            logger.warn("ViaVersion is not installed, cannot attach client protocol to profile.");
            return;
        }

        final int protocol;
        try {
            protocol = Via.getAPI().getPlayerVersion(event.getPlayer().getUniqueId());
        } catch (Exception e) {
            logger.warn("Failed to get ViaVersion protocol for {}", event.getPlayer().getGameProfile().getName(), e);
            return;
        }

        event.getPlayer().setGameProfileProperties(List.of(
                new GameProfile.Property(
                        PROTOCOL_PROPERTY,
                        Integer.toString(protocol),
                        ""
                )
        ));
    }
}