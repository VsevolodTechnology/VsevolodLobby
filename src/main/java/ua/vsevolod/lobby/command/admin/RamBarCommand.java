package ua.vsevolod.lobby.command.admin;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.admin.StatsBarService;
import ua.vsevolod.lobby.util.Messages;

public class RamBarCommand extends AdminCommand {

    public RamBarCommand() {
        super("rambar");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            boolean shown = StatsBarService.get().toggleRam(p);
            p.sendMessage(shown
                    ? Messages.success("RAM-бар включён.")
                    : Messages.warning("RAM-бар выключен."));
        });
    }
}
