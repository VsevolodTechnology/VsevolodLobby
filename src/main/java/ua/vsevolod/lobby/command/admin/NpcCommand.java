package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcDefinition;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcPosition;
import ua.vsevolod.lobby.util.Messages;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NpcCommand extends AdminCommand {

    private final NpcManager manager;

    public NpcCommand(NpcManager manager) {
        super("npc");
        this.manager = manager;

        setDefaultExecutor((sender, ctx) -> usage(sender));

        ArgumentLiteral listArg       = new ArgumentLiteral("list");
        ArgumentLiteral addArg        = new ArgumentLiteral("add");
        ArgumentLiteral removeArg     = new ArgumentLiteral("remove");
        ArgumentLiteral moveArg       = new ArgumentLiteral("move");
        ArgumentLiteral setnameArg    = new ArgumentLiteral("setname");
        ArgumentLiteral setdescArg    = new ArgumentLiteral("setdesc");
        ArgumentLiteral setskinArg    = new ArgumentLiteral("setskin");
        ArgumentLiteral setglowArg    = new ArgumentLiteral("setglow");
        ArgumentLiteral setglowcolorArg = new ArgumentLiteral("setglowcolor");
        ArgumentLiteral setvisibleArg = new ArgumentLiteral("setvisible");
        ArgumentLiteral setactionArg  = new ArgumentLiteral("setaction");

        ArgumentString idArg = ArgumentType.String("id");
        ArgumentString existingIdArg = ArgumentType.String("id");
        existingIdArg.setSuggestionCallback((sender, ctx, suggestion) -> {
            for (String id : manager.allIds()) suggestion.addEntry(new SuggestionEntry(id));
        });
        ArgumentStringArray restArg = ArgumentType.StringArray("rest");
        ArgumentLiteral sideRight = new ArgumentLiteral("right");
        ArgumentLiteral sideLeft  = new ArgumentLiteral("left");

        addSyntax((sender, ctx) -> list(sender), listArg);
        addSyntax((sender, ctx) -> add(sender, ctx.get(idArg)), addArg, idArg);
        addSyntax((sender, ctx) -> remove(sender, ctx.get(existingIdArg)), removeArg, existingIdArg);
        addSyntax((sender, ctx) -> move(sender, ctx.get(existingIdArg)), moveArg, existingIdArg);
        addSyntax((sender, ctx) -> setName(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setnameArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setDesc(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setdescArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setSkin(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setskinArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setGlow(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setglowArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setGlowColor(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setglowcolorArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setVisible(sender, ctx.get(existingIdArg), join(ctx.get(restArg))),
                setvisibleArg, existingIdArg, restArg);
        addSyntax((sender, ctx) -> setAction(sender, ctx.get(existingIdArg), "right", join(ctx.get(restArg))),
                setactionArg, existingIdArg, sideRight, restArg);
        addSyntax((sender, ctx) -> setAction(sender, ctx.get(existingIdArg), "left", join(ctx.get(restArg))),
                setactionArg, existingIdArg, sideLeft, restArg);
    }

    private void usage(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.sendMessage(Messages.info("Команда /npc"));
        p.sendMessage(Messages.compose(Messages.accent("/npc list")));
        p.sendMessage(Messages.compose(Messages.accent("/npc add <id>"), Messages.muted(" — создаёт NPC на твоей позиции")));
        p.sendMessage(Messages.compose(Messages.accent("/npc remove <id>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc move <id>"), Messages.muted(" — переносит на твою позицию")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setname <id> <текст | none>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setdesc <id> <текст | none>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setskin <id> <ник | url:https://... | none>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setglow <id> <true|false>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setglowcolor <id> <white|red|gold|aqua|… | none>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setvisible <id> <true|false>")));
        p.sendMessage(Messages.compose(Messages.accent("/npc setaction <id> <right|left> <[prefix] команда ...>")));
        p.sendMessage(Messages.compose(Messages.muted("  Пример: /npc setaction mob right [menu] mode-selector")));
        p.sendMessage(Messages.compose(Messages.muted("  Пример: /npc setaction mob right [player] server adventure")));
        p.sendMessage(Messages.compose(Messages.muted("  Несколько команд — используй /reload и редактируй npcs.yml напрямую")));
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        List<NpcDefinition> all = manager.all();
        if (all.isEmpty()) { p.sendMessage(Messages.warning("NPC нет.")); return; }
        p.sendMessage(Messages.compose(
                Messages.text("NPC: "),
                Messages.accent(String.valueOf(all.size()))));
        for (NpcDefinition d : all) {
            p.sendMessage(Messages.compose(
                    Messages.accent(d.id()),
                    Messages.muted(" @ "),
                    Messages.text(String.format("%.1f / %.1f / %.1f",
                            d.position().x(), d.position().y(), d.position().z())),
                    Messages.muted(" | name="),
                    Messages.text(d.name() == null ? "—" : d.name()),
                    Messages.muted(" skin="),
                    Messages.text(d.skin() == null ? "—" : d.skin())));
            if (!d.rightClickCommands().isEmpty())
                p.sendMessage(Messages.compose(
                        Messages.muted("  right: "),
                        Messages.text(String.join(", ", d.rightClickCommands()))));
            if (!d.leftClickCommands().isEmpty())
                p.sendMessage(Messages.compose(
                        Messages.muted("  left:  "),
                        Messages.text(String.join(", ", d.leftClickCommands()))));
        }
    }

    private void add(CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        if (manager.findById(id).isPresent()) {
            p.sendMessage(Messages.compose(
                    Messages.errorText("NPC с id "),
                    Messages.accent("'" + id + "'"),
                    Messages.errorText(" уже существует.")));
            return;
        }
        NpcDefinition def = new NpcDefinition(
                id, NpcPosition.from(p.getPosition()),
                null, null, null, false, null, true,
                List.of(), List.of(),
                null, 1.0
        );
        applyEdit(p, "добавлен", appended(def));
    }

    private void remove(CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        if (manager.findById(id).isEmpty()) {
            p.sendMessage(notFound(id));
            return;
        }
        applyEdit(p, "удалён", withoutId(id));
    }

    private void move(CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        applyEdit(p, "перемещён", replace(existing.get().withPosition(NpcPosition.from(p.getPosition()))));
    }

    private void setName(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        String name = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value;
        applyEdit(p, "имя обновлено", replace(existing.get().withName(name)));
    }

    private void setDesc(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        String desc = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value;
        applyEdit(p, "описание обновлено", replace(existing.get().withDescription(desc)));
    }

    private void setSkin(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        String skin = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value.trim();
        applyEdit(p, "скин обновлён", replace(existing.get().withSkin(skin)));
    }

    private void setGlow(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        boolean glow = Boolean.parseBoolean(value.trim());
        applyEdit(p, "glow=" + glow, replace(existing.get().withGlowing(glow)));
    }

    private void setGlowColor(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        String trimmed = value.trim();
        String color = "none".equalsIgnoreCase(trimmed) || trimmed.isEmpty() ? null : trimmed.toLowerCase();
        applyEdit(p, "glow_color=" + (color == null ? "none" : color), replace(existing.get().withGlowColor(color)));
    }

    private void setVisible(CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }
        boolean vis = Boolean.parseBoolean(value.trim());
        applyEdit(p, "visible=" + vis, replace(existing.get().withVisible(vis)));
    }

    /**
     * Sets a SINGLE command for the given click side.
     * To set multiple commands, edit npcs.yml directly and use /reload.
     */
    private void setAction(CommandSender sender, String id, String side, String rest) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage(notFound(id)); return; }

        String trimmed = rest.trim();
        if (trimmed.isBlank()) {
            p.sendMessage(Messages.compose(
                    Messages.errorText("Использование: "),
                    Messages.accent("/npc setaction <id> " + side + " <[prefix] команда | none>")));
            return;
        }

        List<String> commands = "none".equalsIgnoreCase(trimmed) ? List.of() : List.of(trimmed);
        NpcDefinition d = existing.get();
        NpcDefinition next = "right".equals(side)
                ? d.withRightClickCommands(commands)
                : d.withLeftClickCommands(commands);
        applyEdit(p, side + "-click → " + (commands.isEmpty() ? "none" : trimmed), replace(next));
    }

    private static net.kyori.adventure.text.Component notFound(String id) {
        return Messages.compose(
                Messages.errorText("NPC "),
                Messages.accent("'" + id + "'"),
                Messages.errorText(" не найден."));
    }

    // --- List-mutation helpers ---

    private List<NpcDefinition> appended(NpcDefinition def) {
        List<NpcDefinition> next = new ArrayList<>(manager.all());
        next.add(def);
        return next;
    }

    private List<NpcDefinition> withoutId(String id) {
        List<NpcDefinition> next = new ArrayList<>();
        for (NpcDefinition d : manager.all()) {
            if (!d.id().equalsIgnoreCase(id)) next.add(d);
        }
        return next;
    }

    private List<NpcDefinition> replace(NpcDefinition replacement) {
        List<NpcDefinition> next = new ArrayList<>();
        for (NpcDefinition d : manager.all()) {
            next.add(d.id().equals(replacement.id()) ? replacement : d);
        }
        return next;
    }

    private void applyEdit(Player p, String summary, List<NpcDefinition> newList) {
        try {
            manager.updateAndSave(newList);
            p.sendMessage(Messages.compose(
                    Messages.successText("ОК — "),
                    Messages.text(summary)));
        } catch (Exception e) {
            p.sendMessage(Messages.error("Ошибка: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private static String join(String[] tokens) {
        if (tokens == null || tokens.length == 0) return "";
        return String.join(" ", tokens);
    }
}
