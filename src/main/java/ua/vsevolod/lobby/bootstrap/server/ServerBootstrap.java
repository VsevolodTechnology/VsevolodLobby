package ua.vsevolod.lobby.bootstrap.server;

import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.bootstrap.module.CommandModule;
import ua.vsevolod.lobby.bootstrap.module.InstanceModule;
import ua.vsevolod.lobby.bootstrap.module.LobbyModule;
import ua.vsevolod.lobby.bootstrap.module.SparkModule;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.integration.console.ConsoleListener;
import ua.vsevolod.lobby.integration.console.ShutdownHook;

public class ServerBootstrap {

    public static void bootstrap() {
//        var server = MinecraftServer.init(new Auth.Velocity("vUmJPztt36VL"));
        var server = MinecraftServer.init();

        ShutdownHook.register();
        ConsoleListener.start();

        ModuleLoader loader = new ModuleLoader();

        loader.register(new SparkModule());
        loader.register(new CommandModule());
        loader.register(new InstanceModule());
        loader.register(new LobbyModule());

        loader.loadAll();

        server.start(LobbyConfig.Settings.HOST);
    }

}
