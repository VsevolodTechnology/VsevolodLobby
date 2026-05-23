package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.BlockHandler;
import ua.vsevolod.lobby.bootstrap.module.CommandModule;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.bootstrap.module.SparkModule;
import ua.vsevolod.lobby.config.LoggingConfig;
import ua.vsevolod.lobby.config.LuckPermsConfig;
import ua.vsevolod.lobby.config.ProxyConfig;
import ua.vsevolod.lobby.config.SocialsConfig;
import ua.vsevolod.lobby.integration.luckperms.LuckPermsService;
import ua.vsevolod.lobby.config.server.ServersConfig;
import ua.vsevolod.lobby.feature.admin.config.CommandsReferenceWriter;
import ua.vsevolod.lobby.util.ServerLogger;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcsConfig;
import ua.vsevolod.lobby.feature.lobby.player.behavior.PlayerBehaviorConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.items.JoinItemsConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.welcome.WelcomeConfig;
import ua.vsevolod.lobby.feature.lobby.interaction.qr.QrCardConfig;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfig;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfig;
import ua.vsevolod.lobby.feature.lobby.ui.sidebar.SidebarConfig;
import ua.vsevolod.lobby.feature.lobby.ui.tab.TabConfig;
import ua.vsevolod.lobby.integration.console.ConsoleListener;
import ua.vsevolod.lobby.integration.console.ShutdownHook;

import java.net.InetSocketAddress;

public class ServerBootstrap {

    public static void bootstrap() {
        long bootStart = System.nanoTime();

        // Init logger with defaults first so anything below can log
        ServerLogger.init(LoggingConfig.load());
        ServerLogger log = ServerLogger.get();
        log.info("Server initialization started");

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
            log.info("Velocity modern forwarding enabled");
        }

        // All configs are ConfigLib-backed — each init() loads the file and registers a
        // /reload hook with ConfigReload.
        SocialsConfig.init();
        ServersConfig.init();
        TabConfig.init();
        SidebarConfig.init();
        NpcsConfig.init();
        JoinItemsConfig.init();
        MenusConfig.init();
        HologramsConfig.init();
        PlayerBehaviorConfig.init();
        WelcomeConfig.init();
        CutsceneConfig.init();
        QrCardConfig.init();
        CommandsReferenceWriter.write();

        // Optional LuckPerms integration — gated on config/system/luckperms.yml { enabled: true }.
        // Must run AFTER Minestom init (it registers events) and BEFORE CommandModule so that
        // /luckperms is registered together with the rest of our commands.
        LuckPermsService.init(LuckPermsConfig.load());
        ua.vsevolod.lobby.bootstrap.LobbyShutdown.register(LuckPermsService::shutdown);

        ShutdownHook.register();
        ConsoleListener.start();

        ModuleLoader loader = new ModuleLoader();
        loader.register(new SparkModule());
        loader.register(new CommandModule());
        loader.register(new InstanceModule());
        loader.register(new LobbyModule());
        loader.loadAll();

        // ── Bind decision ────────────────────────────────────────────────
        // When the embedded ViaProxy bridge is enabled, the EXTERNAL hostPort is owned by
        // ViaProxy and Minestom binds to 127.0.0.1:internalPort for the loopback hop.
        // Otherwise Minestom binds directly to the external hostPort as before.
        boolean viaEnabled = proxyConfig.viaProxyEnabled();
        String bindAddress = viaEnabled ? "127.0.0.1" : proxyConfig.hostAddress();
        int bindPort = viaEnabled ? proxyConfig.internalPort() : proxyConfig.hostPort();
        server.start(new InetSocketAddress(bindAddress, bindPort));

        if (viaEnabled) {
            try {
                ua.vsevolod.lobby.feature.admin.via.ViaProxyBridge bridge =
                        new ua.vsevolod.lobby.feature.admin.via.ViaProxyBridge();
                bridge.start(
                        proxyConfig.hostAddress(),
                        proxyConfig.hostPort(),
                        "127.0.0.1",
                        proxyConfig.internalPort(),
                        proxyConfig.viaTargetVersion());
                ua.vsevolod.lobby.bootstrap.LobbyShutdown.register(bridge::stop);
            } catch (Exception e) {
                log.error("Failed to start embedded ViaProxy: " + e.getMessage()
                        + " — legacy clients will be unable to connect");
                e.printStackTrace();
            }
        }

        double uptimeSec = (System.nanoTime() - bootStart) / 1_000_000_000.0;
        ServerLogger.get().pterodactylReady(uptimeSec);
    }
}
