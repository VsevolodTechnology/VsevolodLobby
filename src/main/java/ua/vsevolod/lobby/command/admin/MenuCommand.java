package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.ui.menu.MenuManager;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenuDefinition;
import ua.vsevolod.lobby.feature.lobby.ui.menu.config.MenusConfig;
import ua.vsevolod.lobby.util.Messages;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MenuCommand extends AdminCommand {

    private final MenuManager manager;

    public MenuCommand(MenuManager manager) {
        super("menu");
        this.manager = manager;

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
    }

    private void usage(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.sendMessage(Messages.info("Команда /menu"));
        p.sendMessage(Messages.compose(Messages.accent("/menu list")));
        p.sendMessage(Messages.compose(Messages.accent("/menu open <id>")));
        p.sendMessage(Messages.compose(Messages.accent("/menu setvisibility <id> <all|bypass-only>")));
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        MenusConfig cfg = MenusConfig.get();
        if (cfg.menus.isEmpty()) {
            p.sendMessage(Messages.warning("Меню не настроены."));
            return;
        }
        p.sendMessage(Messages.compose(
                Messages.text("Меню: "),
                Messages.accent(String.valueOf(cfg.menus.size()))));
        for (MenuDefinition def : cfg.menus.values()) {
            p.sendMessage(Messages.compose(
                    Messages.accent(def.id()),
                    Messages.muted(" — " + def.rows() + " строк, видимость "),
                    Messages.text(def.visibility().toYaml()),
                    Messages.muted(", items: "),
                    Messages.text(String.valueOf(def.items().size()))));
        }
    }

    private void openFor(CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        boolean ok = manager.openFor(p, id);
        if (!ok) p.sendMessage(Messages.compose(
                Messages.errorText("Меню "),
                Messages.accent("'" + id + "'"),
                Messages.errorText(" не найдено.")));
    }

    private void setVisibility(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        MenusConfig cfg = MenusConfig.get();
        MenuDefinition def = cfg.menus.get(id);
        if (def == null) {
            p.sendMessage(Messages.compose(
                    Messages.errorText("Меню "),
                    Messages.accent("'" + id + "'"),
                    Messages.errorText(" не найдено.")));
            return;
        }
        MenuDefinition.Visibility newVis = MenuDefinition.Visibility.fromString(value);
        MenuDefinition updated = def.withVisibility(newVis);

        Map<String, MenuDefinition> next = new LinkedHashMap<>(cfg.menus);
        next.put(id, updated);
        try {
            cfg.menus = next;
            MenusConfig.save(cfg);
            p.sendMessage(Messages.compose(
                    Messages.accent(id),
                    Messages.successText(" видимость = "),
                    Messages.accent(newVis.toYaml())));
        } catch (Exception e) {
            p.sendMessage(Messages.error("Ошибка сохранения: " + e.getMessage()));
            e.printStackTrace();
        }
    }
}
