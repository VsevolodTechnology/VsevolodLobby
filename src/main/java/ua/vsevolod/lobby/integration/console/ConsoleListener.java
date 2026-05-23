package ua.vsevolod.lobby.integration.console;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.builder.CommandResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class ConsoleListener {

    public static void start() {

        ConsoleSender console = MinecraftServer.getCommandManager().getConsoleSender();

        Thread thread = new Thread(() -> {

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(System.in))) {

                String line;

                while ((line = reader.readLine()) != null) {

                    if (line.isBlank()) continue;

                    CommandResult result =
                            MinecraftServer.getCommandManager().execute(console, line);

                    switch (result.getType()) {

                        case SUCCESS -> {
                        }

                        case INVALID_SYNTAX ->
                            System.out.println("[Console] Invalid syntax: " + result.getInput());

                        case CANCELLED ->
                            System.out.println("[Console] Command cancelled: " + result.getInput());

                        case UNKNOWN ->
                            System.out.println("[Console] Unknown command: " + result.getInput());
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

        }, "console-thread");

        thread.setDaemon(true);
        thread.start();
    }
}