package ua.vsevolod.lobby.command.admin;

import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentString;
import net.minestom.server.command.builder.arguments.ArgumentStringArray;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.config.LobbyConfig;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.NpcManager;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcDefinition;
import ua.vsevolod.lobby.feature.lobby.interaction.npc.config.NpcPosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NpcCommand extends Command {

    private final NpcManager manager;

    public NpcCommand(NpcManager manager) {
        super("npc");
        this.manager = manager;

        setCondition((sender, commandString) ->
                sender instanceof Player p && LobbyConfig.Settings.BYPASS_USERS.contains(p.getUsername()));

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

        MinecraftServer.getCommandManager().register(this);
    }

    private void usage(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.sendMessage("§6=== /npc ===");
        p.sendMessage("§e/npc list");
        p.sendMessage("§e/npc add <id> §7— создаёт NPC на твоей позиции");
        p.sendMessage("§e/npc remove <id>");
        p.sendMessage("§e/npc move <id> §7— переносит на твою позицию");
        p.sendMessage("§e/npc setname <id> <текст | none>");
        p.sendMessage("§e/npc setdesc <id> <текст | none>");
        p.sendMessage("§e/npc setskin <id> <ник | url:https://... | none>");
        p.sendMessage("§e/npc setglow <id> <true|false>");
        p.sendMessage("§e/npc setglowcolor <id> <white|red|gold|aqua|… | none>");
        p.sendMessage("§e/npc setvisible <id> <true|false>");
        p.sendMessage("§e/npc setaction <id> <right|left> <[prefix] команда ...>");
        p.sendMessage("§7  Пример: /npc setaction mob right [menu] mode-selector");
        p.sendMessage("§7  Пример: /npc setaction mob right [player] server adventure");
        p.sendMessage("§7  Несколько команд — используй /reload и редактируй npcs.yml напрямую");
    }

    private void list(net.minestom.server.command.CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        List<NpcDefinition> all = manager.all();
        if (all.isEmpty()) { p.sendMessage("§7NPC нет."); return; }
        p.sendMessage("§6=== NPC (" + all.size() + ") ===");
        for (NpcDefinition d : all) {
            p.sendMessage(String.format(
                    "§e%s §7@ §f%.1f §7/ §f%.1f §7/ §f%.1f §7| name=§f%s §7skin=§f%s",
                    d.id(), d.position().x(), d.position().y(), d.position().z(),
                    d.name() == null ? "—" : d.name(),
                    d.skin() == null ? "—" : d.skin()));
            if (!d.rightClickCommands().isEmpty())
                p.sendMessage("§7  right: §f" + String.join("§7, §f", d.rightClickCommands()));
            if (!d.leftClickCommands().isEmpty())
                p.sendMessage("§7  left:  §f" + String.join("§7, §f", d.leftClickCommands()));
        }
    }

    private void add(net.minestom.server.command.CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        if (manager.findById(id).isPresent()) {
            p.sendMessage("§cNPC с id '" + id + "' уже существует.");
            return;
        }
        NpcDefinition def = new NpcDefinition(
                id, NpcPosition.from(p.getPosition()),
                null, null, null, false, null, true,
                List.of(), List.of()
        );
        applyEdit(p, "добавлен", appended(def));
    }

    private void remove(net.minestom.server.command.CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        if (manager.findById(id).isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        applyEdit(p, "удалён", withoutId(id));
    }

    private void move(net.minestom.server.command.CommandSender sender, String id) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        applyEdit(p, "перемещён", replace(existing.get().withPosition(NpcPosition.from(p.getPosition()))));
    }

    private void setName(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        String name = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value;
        applyEdit(p, "имя обновлено", replace(existing.get().withName(name)));
    }

    private void setDesc(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        String desc = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value;
        applyEdit(p, "описание обновлено", replace(existing.get().withDescription(desc)));
    }

    private void setSkin(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        String skin = "none".equalsIgnoreCase(value.trim()) || value.isBlank() ? null : value.trim();
        applyEdit(p, "скин обновлён", replace(existing.get().withSkin(skin)));
    }

    private void setGlow(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        boolean glow = Boolean.parseBoolean(value.trim());
        applyEdit(p, "glow=" + glow, replace(existing.get().withGlowing(glow)));
    }

    private void setGlowColor(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        String trimmed = value.trim();
        String color = "none".equalsIgnoreCase(trimmed) || trimmed.isEmpty() ? null : trimmed.toLowerCase();
        applyEdit(p, "glow_color=" + (color == null ? "none" : color), replace(existing.get().withGlowColor(color)));
    }

    private void setVisible(net.minestom.server.command.CommandSender sender, String id, String value) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }
        boolean vis = Boolean.parseBoolean(value.trim());
        applyEdit(p, "visible=" + vis, replace(existing.get().withVisible(vis)));
    }

    /**
     * Sets a SINGLE command for the given click side.
     * To set multiple commands, edit npcs.yml directly and use /reload.
     *
     * Examples:
     *   /npc setaction mob-id right [menu] mode-selector
     *   /npc setaction mob-id right [player] server adventure
     *   /npc setaction mob-id right [connect] lobby
     *   /npc setaction mob-id left [message] &aПривет!
     *   /npc setaction mob-id right none   ← clears the action
     */
    private void setAction(net.minestom.server.command.CommandSender sender, String id, String side, String rest) {
        if (!(sender instanceof Player p)) return;
        Optional<NpcDefinition> existing = manager.findById(id);
        if (existing.isEmpty()) { p.sendMessage("§cNPC '" + id + "' не найден."); return; }

        String trimmed = rest.trim();
        if (trimmed.isBlank()) {
            p.sendMessage("§c/npc setaction <id> " + side + " <[prefix] команда | none>");
            return;
        }

        List<String> commands = "none".equalsIgnoreCase(trimmed) ? List.of() : List.of(trimmed);
        NpcDefinition d = existing.get();
        NpcDefinition next = "right".equals(side)
                ? d.withRightClickCommands(commands)
                : d.withLeftClickCommands(commands);
        applyEdit(p, side + "-click → " + (commands.isEmpty() ? "none" : trimmed), replace(next));
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
            p.sendMessage("§aОК — " + summary);
        } catch (Exception e) {
            p.sendMessage("§cОшибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String join(String[] tokens) {
        if (tokens == null || tokens.length == 0) return "";
        return String.join(" ", tokens);
    }
}
