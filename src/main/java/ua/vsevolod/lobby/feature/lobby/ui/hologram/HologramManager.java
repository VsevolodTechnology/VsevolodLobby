package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.timer.TaskSchedule;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramDefinition;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages lobby holograms from {@code holograms.yml} — including the welcome greeting, which
 * is now just a regular entry in the {@code holograms} map (no separate file/service).
 *
 * <p>Lines support the {@code {online}} placeholder; holograms that use it are refreshed
 * every few seconds so the count stays live.</p>
 */
public final class HologramManager {

    private static final String ONLINE_PLACEHOLDER = "{online}";

    private volatile Map<String, TextHologram> live = Map.of();
    private volatile Map<String, HologramDefinition> defs = Map.of();
    private boolean refreshStarted = false;

    /**
     * Called when the config is loaded or reloaded.
     * Rebuilds all holograms and live-updates every online lobby player.
     */
    public void onConfigApplied(HologramsConfig config) {
        Map<String, TextHologram> old = this.live;

        Map<String, TextHologram> built = new LinkedHashMap<>();
        Map<String, HologramDefinition> newDefs = new LinkedHashMap<>();
        for (HologramDefinition def : config.holograms.values()) {
            built.put(def.id(), buildHologram(def));
            newDefs.put(def.id(), def);
        }
        this.live = Map.copyOf(built);
        this.defs = Map.copyOf(newDefs);

        // Live-refresh for players who are already online (e.g. /reload)
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        for (Player player : online) {
            for (TextHologram h : old.values()) h.hide(player);
            for (TextHologram h : built.values()) h.show(player);
        }

        startRefreshIfNeeded();
    }

    public void showTo(Player player) {
        for (TextHologram h : live.values()) h.show(player);
    }

    public void hideFrom(Player player) {
        for (TextHologram h : live.values()) h.hide(player);
    }

    // ── Live {online} refresh ──────────────────────────────────────────────────

    private synchronized void startRefreshIfNeeded() {
        if (refreshStarted) return;
        refreshStarted = true;
        MinecraftServer.getSchedulerManager()
                .buildTask(this::refreshDynamic)
                .repeat(TaskSchedule.seconds(5))
                .schedule();
    }

    /** Re-renders holograms that use {@code {online}} so the count stays current. */
    private void refreshDynamic() {
        Map<String, TextHologram> liveSnap = this.live;
        Map<String, HologramDefinition> defsSnap = this.defs;
        if (liveSnap.isEmpty()) return;
        Collection<Player> players = MinecraftServer.getConnectionManager().getOnlinePlayers();
        if (players.isEmpty()) return;

        for (Map.Entry<String, HologramDefinition> e : defsSnap.entrySet()) {
            HologramDefinition def = e.getValue();
            if (!usesOnline(def)) continue;
            TextHologram hologram = liveSnap.get(e.getKey());
            if (hologram == null || hologram.entries.isEmpty()) continue;
            hologram.updateLineTextAll(players, 0, combinedText(def));
        }
    }

    private static boolean usesOnline(HologramDefinition def) {
        for (String line : def.lines()) {
            if (line.contains(ONLINE_PLACEHOLDER)) return true;
        }
        return false;
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    private static TextHologram buildHologram(HologramDefinition def) {
        TextHologramStyle style = TextHologramStyle.defaults()
                .billboard(parseBillboard(def.billboard()))
                .scale(new Vec(def.scaleX(), def.scaleY(), def.scaleZ()))
                .shadow(def.shadow())
                .seeThrough(def.seeThrough())
                .alignment(parseAlignment(def.alignment()))
                .useDefaultBackground(def.useDefaultBackground())
                .backgroundColor(def.backgroundColor());

        return new TextHologramBuilder(new Pos(def.x(), def.y(), def.z()))
                .line(combinedText(def), style)
                .build();
    }

    /**
     * Combines all lines into ONE Component with embedded newlines (a single text_display
     * entity), substituting the {@code {online}} placeholder with the current player count.
     */
    private static Component combinedText(HologramDefinition def) {
        int online = MinecraftServer.getConnectionManager().getOnlinePlayerCount();
        Component combined = null;
        for (String line : def.lines()) {
            Component c = line.contains(ONLINE_PLACEHOLDER)
                    ? Text.raw(line.replace(ONLINE_PLACEHOLDER, Integer.toString(online)))
                    : Text.c(line);
            combined = (combined == null) ? c : combined.appendNewline().append(c);
        }
        return combined == null ? Component.empty() : combined;
    }

    private static AbstractDisplayMeta.BillboardConstraints parseBillboard(String s) {
        return switch (s.toLowerCase()) {
            case "vertical"   -> AbstractDisplayMeta.BillboardConstraints.VERTICAL;
            case "horizontal" -> AbstractDisplayMeta.BillboardConstraints.HORIZONTAL;
            case "fixed"      -> AbstractDisplayMeta.BillboardConstraints.FIXED;
            default           -> AbstractDisplayMeta.BillboardConstraints.CENTER;
        };
    }

    private static TextDisplayMeta.Alignment parseAlignment(String s) {
        return switch (s.toLowerCase()) {
            case "left"  -> TextDisplayMeta.Alignment.LEFT;
            case "right" -> TextDisplayMeta.Alignment.RIGHT;
            default      -> TextDisplayMeta.Alignment.CENTER;
        };
    }
}
