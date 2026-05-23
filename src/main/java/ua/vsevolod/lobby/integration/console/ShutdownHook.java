package ua.vsevolod.lobby.integration.console;

import net.minestom.server.MinecraftServer;
import ua.vsevolod.lobby.bootstrap.LobbyShutdown;

public class ShutdownHook {

    public static void register() {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SERVER] JVM shutdown detected");
            LobbyShutdown.flushAllPersistence();
            MinecraftServer.stopCleanly();
        }));

    }
}