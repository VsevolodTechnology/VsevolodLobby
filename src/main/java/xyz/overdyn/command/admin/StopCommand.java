package xyz.overdyn.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.ConsoleSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import xyz.overdyn.config.LobbyConfig;

import java.time.Duration;

public class StopCommand extends Command {

    public StopCommand() {
        super("stop");

        setCondition((sender, commandString) -> (sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())));

        setDefaultExecutor((sender, context) -> {

            if (!(sender instanceof ConsoleSender || (sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())))) {
                return;
            }

            System.out.println("[SERVER] Stopping server...");
            LobbyConfig.Settings.SHUTTING_DOWN = true;
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(e -> e.kick(LobbyConfig.Messages.SHUTTING_DOWN_KICKING_MSG));
            MinecraftServer.getSchedulerManager().buildTask(() -> {
                MinecraftServer.stopCleanly();

                System.out.println("[SERVER] Shutdown complete.");
                System.exit(0);
            }).delay(Duration.ofMillis(300)).schedule();
        });


        MinecraftServer.getCommandManager().register(this);
    }
}
