package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.bootstrap.module.CommandModule;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.bootstrap.module.SparkModule;
import ua.vsevolod.lobby.config.ProxyConfig;
import ua.vsevolod.lobby.integration.console.ConsoleListener;
import ua.vsevolod.lobby.integration.console.ShutdownHook;

import java.net.InetSocketAddress;

public class ServerBootstrap {

    public static void bootstrap() {
        ProxyConfig proxyConfig = ProxyConfig.load();

        var server = proxyConfig.velocityEnabled()
                ? MinecraftServer.init(new Auth.Velocity(proxyConfig.velocitySecret()))
                : MinecraftServer.init();

        if (proxyConfig.velocityEnabled()) {
            HandshakeOverride.install();
            System.out.println("[Bootstrap] Velocity modern forwarding ENABLED — accepting any client protocol (Via translates downstream).");
        }

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
