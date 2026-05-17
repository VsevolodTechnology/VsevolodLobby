package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.admin.config.ConfigManager;

public class ReloadCommand extends Command {

    private final ConfigManager configManager;

    public ReloadCommand(ConfigManager configManager) {
        super("reload");
        this.configManager = configManager;

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

        setDefaultExecutor((sender, context) -> {
            if (!(sender instanceof Player p) || !LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername())) {
                return;
            }
            ConfigManager.ReloadResult result = configManager.reloadAll();
            if (result.allOk()) {
                p.sendMessage("§aПерезагружено секций: §f" + result.loaded());
            } else {
                p.sendMessage("§eПерезагружено: §f" + result.loaded()
                        + "§7, ошибки в: §c" + String.join("§7, §c", result.failed()));
                p.sendMessage("§7Подробности в консоли. Старые значения сохранены для упавших секций.");
            }
        });

        MinecraftServer.getCommandManager().register(this);
    }
}
