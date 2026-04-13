package xyz.overdyn.bootstrap.server;

import net.minestom.server.MinecraftServer;
import xyz.overdyn.bootstrap.module.CommandModule;
import xyz.overdyn.bootstrap.module.InstanceModule;
import xyz.overdyn.bootstrap.module.LobbyModule;
import xyz.overdyn.bootstrap.module.SparkModule;
import xyz.overdyn.config.LobbyConfig;
import xyz.overdyn.integration.console.ConsoleListener;
import xyz.overdyn.integration.console.ShutdownHook;

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
