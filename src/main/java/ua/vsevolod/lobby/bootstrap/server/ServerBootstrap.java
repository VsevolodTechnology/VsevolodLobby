package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.BlockHandler;
import ua.vsevolod.lobby.bootstrap.module.CommandModule;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.bootstrap.module.SparkModule;
import ua.vsevolod.lobby.config.ProxyConfig;
import ua.vsevolod.lobby.feature.admin.config.CommandsReferenceWriter;
import ua.vsevolod.lobby.feature.admin.config.ConfigManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcConfigSection;
import ua.vsevolod.lobby.feature.lobby.player.behavior.PlayerBehaviorConfigSection;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfigSection;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfigSection;
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

        // Ensure a custom handler is set so that if any tick-level exception occurs, the
        // ExceptionManager does not fall back to its InvokeDynamic default path, which throws
        // NoClassDefFoundError for ExceptionHandler on some JVM / JAR combinations.
        MinecraftServer.getExceptionManager().setExceptionHandler(e -> e.printStackTrace());

        // Register dummy handlers for sign block variants so Minestom doesn't spam warnings
        // when loading lobby world chunks that contain signs, and avoids potential NPE paths
        // in block processing that can trigger the ExceptionManager crash path.
        String[] signNamespaces = {
            "minecraft:sign", "minecraft:oak_sign", "minecraft:spruce_sign",
            "minecraft:birch_sign", "minecraft:jungle_sign", "minecraft:acacia_sign",
            "minecraft:dark_oak_sign", "minecraft:mangrove_sign", "minecraft:cherry_sign",
            "minecraft:bamboo_sign", "minecraft:crimson_sign", "minecraft:warped_sign",
            "minecraft:oak_wall_sign", "minecraft:spruce_wall_sign", "minecraft:birch_wall_sign",
            "minecraft:jungle_wall_sign", "minecraft:acacia_wall_sign", "minecraft:dark_oak_wall_sign",
            "minecraft:mangrove_wall_sign", "minecraft:cherry_wall_sign", "minecraft:bamboo_wall_sign",
            "minecraft:crimson_wall_sign", "minecraft:warped_wall_sign",
            "minecraft:oak_hanging_sign", "minecraft:spruce_hanging_sign", "minecraft:birch_hanging_sign",
            "minecraft:jungle_hanging_sign", "minecraft:acacia_hanging_sign",
            "minecraft:dark_oak_hanging_sign", "minecraft:mangrove_hanging_sign",
            "minecraft:cherry_hanging_sign", "minecraft:bamboo_hanging_sign",
            "minecraft:crimson_hanging_sign", "minecraft:warped_hanging_sign",
            "minecraft:oak_wall_hanging_sign", "minecraft:spruce_wall_hanging_sign",
            "minecraft:birch_wall_hanging_sign", "minecraft:jungle_wall_hanging_sign",
            "minecraft:acacia_wall_hanging_sign", "minecraft:dark_oak_wall_hanging_sign",
            "minecraft:mangrove_wall_hanging_sign", "minecraft:cherry_wall_hanging_sign",
            "minecraft:bamboo_wall_hanging_sign", "minecraft:crimson_wall_hanging_sign",
            "minecraft:warped_wall_hanging_sign"
        };
        for (String ns : signNamespaces) {
            MinecraftServer.getBlockManager().registerHandler(ns, () -> BlockHandler.Dummy.get(ns));
        }

        if (proxyConfig.velocityEnabled()) {
            HandshakeOverride.install();
            System.out.println("[Bootstrap] Velocity modern forwarding ENABLED — accepting any client protocol (Via translates downstream).");
        }

        CONFIG_MANAGER.register(TabConfigSection.INSTANCE);
        CONFIG_MANAGER.register(SidebarConfigSection.INSTANCE);
        CONFIG_MANAGER.register(NpcConfigSection.INSTANCE);
        CONFIG_MANAGER.register(JoinItemsConfigSection.INSTANCE);
        CONFIG_MANAGER.register(MenusConfigSection.INSTANCE);
        CONFIG_MANAGER.register(HologramsConfigSection.INSTANCE);
        CONFIG_MANAGER.register(PlayerBehaviorConfigSection.INSTANCE);
        CONFIG_MANAGER.loadAll();
        CommandsReferenceWriter.write();

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
