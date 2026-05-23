package ua.vsevolod.lobby.command.admin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.bootstrap.LobbyShutdown;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.util.ServerLogger;

import java.time.Duration;

public class StopCommand extends AdminCommand {

    private static final Component KICK_MESSAGE = Component.text(
            "Сервер остановлен.", TextColor.color(0xE36666));

    public StopCommand() {
        super("stop", true, "end");   // allowsConsole=true — server console runs this on shutdown

        setDefaultExecutor((sender, context) -> {
            ServerLogger.get().info("Server stopping...");
            LobbyConfig.Settings.SHUTTING_DOWN = true;
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(e -> e.kick(KICK_MESSAGE));
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                LobbyShutdown.flushAllPersistence();
                MinecraftServer.stopCleanly();
                ServerLogger.get().info("Shutdown complete.");
                System.exit(0);
            }).delay(Duration.ofMillis(300)).schedule();
        });
    }
}
