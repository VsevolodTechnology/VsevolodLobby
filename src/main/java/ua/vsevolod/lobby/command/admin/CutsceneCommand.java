package ua.vsevolod.lobby.command.admin;

import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.arguments.ArgumentLiteral;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.number.ArgumentNumber;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneConfig;
import ua.vsevolod.lobby.feature.lobby.player.join.cutscene.CutsceneService;
import ua.vsevolod.lobby.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /cutscene} — admin tools for the lobby flyover.
 *
 * <ul>
 *   <li>{@code /cutscene play} — replay the flyover for self (testing).</li>
 *   <li>{@code /cutscene add [hold_ticks]} — append your current location/view as a waypoint.</li>
 *   <li>{@code /cutscene remove <idx>} — drop a waypoint by index (1-based).</li>
 *   <li>{@code /cutscene list} — show all waypoints with their coords and hold times.</li>
 *   <li>{@code /cutscene clear} — wipe every waypoint.</li>
 *   <li>{@code /cutscene save} — persist edits to {@code config/cutscene.yml}.</li>
 * </ul>
 *
 * <p>Edits update the in-memory snapshot immediately and survive a {@code /reload} only after
 * {@code /cutscene save} — until then they live only in this process and a restart would lose
 * them.</p>
 */
public final class CutsceneCommand extends AdminCommand {

    private final CutsceneService service;

    public CutsceneCommand(CutsceneService service) {
        super("cutscene");
        this.service = service;

        ArgumentLiteral playArg   = new ArgumentLiteral("play");
        ArgumentLiteral addArg    = new ArgumentLiteral("add");
        ArgumentLiteral removeArg = new ArgumentLiteral("remove");
        ArgumentLiteral listArg   = new ArgumentLiteral("list");
        ArgumentLiteral clearArg  = new ArgumentLiteral("clear");
        ArgumentLiteral saveArg   = new ArgumentLiteral("save");

        ArgumentNumber<Integer> holdArg = ArgumentType.Integer("hold").min(1).max(600);
        ArgumentNumber<Integer> idxArg  = ArgumentType.Integer("idx").min(1);

        setDefaultExecutor((sender, ctx) -> usage(sender));

        addSyntax((sender, ctx) -> play(sender), playArg);
        addSyntax((sender, ctx) -> addPoint(sender, 30), addArg);
        addSyntax((sender, ctx) -> addPoint(sender, ctx.get(holdArg)), addArg, holdArg);
        addSyntax((sender, ctx) -> removePoint(sender, ctx.get(idxArg)), removeArg, idxArg);
        addSyntax((sender, ctx) -> list(sender), listArg);
        addSyntax((sender, ctx) -> clear(sender), clearArg);
        addSyntax((sender, ctx) -> save(sender), saveArg);
    }

    private static void usage(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        p.sendMessage(Messages.info("Команда /cutscene"));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene play"), Messages.muted(" — посмотреть кат-сцену себе")));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene add [hold]"), Messages.muted(" — добавить точку (текущая позиция/обзор)")));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene remove <idx>"), Messages.muted(" — удалить точку по номеру")));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene list"), Messages.muted(" — список точек")));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene clear"), Messages.muted(" — очистить все точки")));
        p.sendMessage(Messages.compose(Messages.accent("/cutscene save"), Messages.muted(" — сохранить в config/cutscene.yml")));
    }

    private void play(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        service.play(p);
        p.sendMessage(Messages.success("Кат-сцена запущена."));
    }

    private void addPoint(CommandSender sender, int holdTicks) {
        if (!(sender instanceof Player p)) return;
        Pos pos = p.getPosition();
        CutsceneConfig.Waypoint wp = new CutsceneConfig.Waypoint(
                pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch(), holdTicks);

        CutsceneConfig cur = CutsceneConfig.get();
        List<CutsceneConfig.Waypoint> next = new ArrayList<>(cur.waypoints);
        next.add(wp);
        cur.waypoints = List.copyOf(next);

        p.sendMessage(Messages.compose(
                Messages.successText("Добавлена точка #" + next.size() + ": "),
                Messages.accent(String.format("%.1f / %.1f / %.1f", pos.x(), pos.y(), pos.z())),
                Messages.muted(" yaw " + Math.round(pos.yaw()) + "° pitch " + Math.round(pos.pitch()) + "°"),
                Messages.muted(", hold " + holdTicks + "t")));
        p.sendMessage(Messages.muted("Не забудь /cutscene save."));
    }

    private void removePoint(CommandSender sender, int oneBasedIdx) {
        if (!(sender instanceof Player p)) return;
        CutsceneConfig cur = CutsceneConfig.get();
        int zero = oneBasedIdx - 1;
        if (zero < 0 || zero >= cur.waypoints.size()) {
            p.sendMessage(Messages.error("Нет точки #" + oneBasedIdx + " (всего " + cur.waypoints.size() + ")"));
            return;
        }
        List<CutsceneConfig.Waypoint> next = new ArrayList<>(cur.waypoints);
        next.remove(zero);
        cur.waypoints = List.copyOf(next);
        p.sendMessage(Messages.success("Удалена точка #" + oneBasedIdx + ". Осталось: " + next.size()));
        p.sendMessage(Messages.muted("Не забудь /cutscene save."));
    }

    private void list(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        CutsceneConfig cur = CutsceneConfig.get();
        if (cur.waypoints.isEmpty()) {
            p.sendMessage(Messages.warning("Точек нет. Используй /cutscene add."));
            return;
        }
        p.sendMessage(Messages.compose(
                Messages.text("Точек: "),
                Messages.accent(String.valueOf(cur.waypoints.size()))));
        int n = 1;
        for (CutsceneConfig.Waypoint w : cur.waypoints) {
            p.sendMessage(Messages.compose(
                    Messages.accent("#" + n++ + " "),
                    Messages.text(String.format("%.1f / %.1f / %.1f", w.x(), w.y(), w.z())),
                    Messages.muted(" yaw " + Math.round(w.yaw()) + "° pitch " + Math.round(w.pitch()) + "°"),
                    Messages.muted(", hold " + w.holdTicks() + "t")));
        }
    }

    private void clear(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        CutsceneConfig.get().waypoints = List.of();
        p.sendMessage(Messages.success("Все точки очищены. Сохрани через /cutscene save."));
    }

    private void save(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        try {
            CutsceneConfig.save(CutsceneConfig.get());
            p.sendMessage(Messages.success("Кат-сцена сохранена в config/cutscene.yml."));
        } catch (Exception e) {
            p.sendMessage(Messages.error("Ошибка сохранения: " + e.getMessage()));
            e.printStackTrace();
        }
    }
}
