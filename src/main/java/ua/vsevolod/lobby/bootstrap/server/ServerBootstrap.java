package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.bootstrap.module.CommandModule;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.bootstrap.module.SparkModule;
import ua.vsevolod.lobby.config.ProxyConfig;
import ua.vsevolod.lobby.feature.admin.config.ConfigManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcConfigSection;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.tab.TabConfigSection;
import ua.vsevolod.lobby.integration.console.ConsoleListener;
import ua.vsevolod.lobby.integration.console.ShutdownHook;

import java.net.InetSocketAddress;

public class ServerBootstrap {

    public static final ConfigManager CONFIG_MANAGER = new ConfigManager();

    public static void bootstrap() {
        ProxyConfig proxyConfig = ProxyConfig.load();

        var server = proxyConfig.velocityEnabled()
                ? MinecraftServer.init(new Auth.Velocity(proxyConfig.velocitySecret()))
                : MinecraftServer.init();

        // Disable per-packet zlib on the backend channel. With Velocity in front, sending compressed
        // packets across loopback is wasted CPU — Velocity handles compression to the real client.
        // Old code had threshold=0 which compresses *every* packet, the worst-case option.
        MinecraftServer.setCompressionThreshold(-1);

        if (proxyConfig.velocityEnabled()) {
            HandshakeOverride.install();
            System.out.println("[Bootstrap] Velocity modern forwarding ENABLED — accepting any client protocol (Via translates downstream).");
        }

        CONFIG_MANAGER.register(TabConfigSection.INSTANCE);
        CONFIG_MANAGER.register(SidebarConfigSection.INSTANCE);
        CONFIG_MANAGER.register(NpcConfigSection.INSTANCE);
        CONFIG_MANAGER.register(JoinItemsConfigSection.INSTANCE);
        CONFIG_MANAGER.register(MenusConfigSection.INSTANCE);
        CONFIG_MANAGER.loadAll();

        ShutdownHook.register();
        ConsoleListener.start();

        ModuleLoader loader = new ModuleLoader();
        loader.register(new SparkModule());
        loader.register(new CommandModule());
        loader.register(new InstanceModule());
        loader.register(new LobbyModule());
        loader.loadAll();

        server.start(new InetSocketAddress(proxyConfig.hostAddress(), proxyConfig.hostPort()));
    }
}
