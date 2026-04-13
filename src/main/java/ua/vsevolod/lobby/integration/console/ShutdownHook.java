package ua.vsevolod.lobby.integration.console;

import net.minestom.server.MinecraftServer;

public class ShutdownHook {

    public static void register() {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SERVER] JVM shutdown detected");
            MinecraftServer.stopCleanly();
        }));

    }
}