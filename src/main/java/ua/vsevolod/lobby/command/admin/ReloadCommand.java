package ua.vsevolod.lobby.command.admin;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.ConfigReload;
import ua.vsevolod.lobby.util.Messages;

/**
 * {@code /reload} — re-reads every ConfigLib-backed config from disk and fires their listeners
 * (live UI re-renders for tab/sidebar/menus/npcs/holograms).
 */
public class ReloadCommand extends AdminCommand {

    public ReloadCommand() {
        super("reload");

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p)) return;
            ConfigReload.Result result = ConfigReload.reloadAll();
            if (result.allOk()) {
                p.sendMessage(Messages.compose(
                        Messages.successText("Перезагружено конфигов: "),
                        Messages.accent(String.valueOf(result.loaded()))));
            } else {
                Component failedList = Component.empty();
                boolean first = true;
                for (String name : result.failed()) {
                    if (!first) failedList = failedList.append(Messages.muted(", "));
                    failedList = failedList.append(Messages.errorText(name));
                    first = false;
                }
                p.sendMessage(Messages.compose(
                        Messages.warningText("Перезагружено: "),
                        Messages.accent(String.valueOf(result.loaded())),
                        Messages.muted(", ошибки в: "),
                        failedList));
                p.sendMessage(Messages.muted(
                        "Подробности в консоли. Старые значения сохранены для упавших конфигов."));
            }
        });
    }
}
