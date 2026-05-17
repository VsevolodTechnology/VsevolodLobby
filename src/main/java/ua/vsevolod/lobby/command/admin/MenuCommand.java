package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.ui.menu.MenuManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenuDefinition;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfig;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfigSection;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MenuCommand extends Command {

    private final MenuManager manager;

    public MenuCommand(MenuManager manager) {
        super("menu");
        this.manager = manager;

        setCondition((sender, cmd) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

        setDefaultExecutor((sender, ctx) -> usage(sender));

        ArgumentLiteral openArg = new ArgumentLiteral("open");
        ArgumentLiteral listArg = new ArgumentLiteral("list");
        ArgumentLiteral setvisArg = new ArgumentLiteral("setvisibility");
        ArgumentString idArg = ArgumentType.String("id");
        idArg.setSuggestionCallback((sender, ctx, suggestion) -> {
            for (String id : manager.menuIds()) suggestion.addEntry(new SuggestionEntry(id));
        });
        ArgumentString visArg = ArgumentType.String("visibility");
        visArg.setSuggestionCallback((sender, ctx, suggestion) -> {
            suggestion.addEntry(new SuggestionEntry("all"));
            suggestion.addEntry(new SuggestionEntry("bypass-only"));
        });

        addSyntax((sender, ctx) -> list(sender), listArg);
        addSyntax((sender, ctx) -> openFor(sender, ctx.get(idArg)), openArg, idArg);
        addSyntax((sender, ctx) -> setVisibility(sender, ctx.get(idArg), ctx.get(visArg)),
                setvisArg, idArg, visArg);

        MinecraftServer.getCommandManager().register(this);
    }

    private void usage(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.sendMessage("§6=== /menu ===");
        p.sendMessage("§e/menu list");
        p.sendMessage("§e/menu open <id>");
        p.sendMessage("§e/menu setvisibility <id> <all|bypass-only>");
    }

    private void list(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        MenusConfig cfg = MenusConfigSection.INSTANCE.current();
        if (cfg.menus().isEmpty()) {
            p.sendMessage("§7Меню не настроены.");
            return;
        }
        p.sendMessage("§6=== Меню (" + cfg.menus().size() + ") ===");
        for (MenuDefinition def : cfg.menus().values()) {
            p.sendMessage(String.format("§e%s §7— %d строк, видимость §f%s§7, items: §f%d",
                    def.id(), def.rows(), def.visibility().toYaml(), def.items().size()));
        }
    }

    private void openFor(net.minestom.server.command.CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        boolean ok = manager.openFor(p, id);
        if (!ok) p.sendMessage("§cМеню '" + id + "' не найдено.");
    }

    private void setVisibility(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        MenusConfig cfg = MenusConfigSection.INSTANCE.current();
        MenuDefinition def = cfg.menus().get(id);
        if (def == null) {
            p.sendMessage("§cМеню '" + id + "' не найдено.");
            return;
        }
        MenuDefinition.Visibility newVis = MenuDefinition.Visibility.fromString(value);
        MenuDefinition updated = def.withVisibility(newVis);

        Map<String, MenuDefinition> next = new LinkedHashMap<>(cfg.menus());
        next.put(id, updated);
        try {
            MenusConfigSection.INSTANCE.saveAndApply(new MenusConfig(next));
            p.sendMessage("§a" + id + " visibility = §f" + newVis.toYaml());
        } catch (Exception e) {
            p.sendMessage("§cОшибка сохранения: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
