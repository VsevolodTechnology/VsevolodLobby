package ua.vsevolod.lobby.command.admin;

import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.admin.StatsBarService;
import ua.vsevolod.lobby.util.Messages;

public class TpsBarCommand extends AdminCommand {

    public TpsBarCommand() {
        super("tpsbar");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            boolean shown = StatsBarService.get().toggleTps(p);
            p.sendMessage(shown
                    ? Messages.success("TPS-бар включён.")
                    : Messages.warning("TPS-бар выключен."));
        });
    }
}
