package xyz.overdyn.feature.parkour;

import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

import java.util.function.Consumer;

public final class ParkourCommand extends Command {

    public ParkourCommand(Consumer<Player> action) {
        super("parkour");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players");
                return;
            }

            action.accept(player);
        });
    }
}
