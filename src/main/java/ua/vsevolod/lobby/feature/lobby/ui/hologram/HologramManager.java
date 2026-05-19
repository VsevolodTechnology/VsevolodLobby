package ua.vsevolod.lobby.feature.lobby.ui.hologram;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramDefinition;
import ua.vsevolod.lobby.feature.lobby.ui.hologram.config.HologramsConfig;
import ua.vsevolod.lobby.util.Text;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages static lobby holograms from {@code holograms.yml}.
 * On config reload, existing viewers have old holograms replaced with the new ones.
 */
public final class HologramManager {

    private volatile Map<String, TextHologram> live = Map.of();

    /**
     * Called when the config is loaded or reloaded.
     * Rebuilds all holograms and live-updates every online lobby player.
     */
    public void onConfigApplied(HologramsConfig config) {
        Map<String, TextHologram> old = this.live;

        Map<String, TextHologram> built = new LinkedHashMap<>();
        for (HologramDefinition def : config.holograms().values()) {
            built.put(def.id(), buildHologram(def));
        }
        this.live = Map.copyOf(built);

        // Live-refresh for players who are already online (e.g. /reload)
        Collection<Player> online = MinecraftServer.getConnectionManager().getOnlinePlayers();
        for (Player player : online) {
            for (TextHologram h : old.values()) h.hide(player);
            for (TextHologram h : built.values()) h.show(player);
        }
    }

    public void showTo(Player player) {
        for (TextHologram h : live.values()) h.show(player);
    }

    public void hideFrom(Player player) {
        for (TextHologram h : live.values()) h.hide(player);
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

        // Combine all lines into ONE Component with embedded newlines — produces a single
        // text_display entity, matching the original behaviour. Multiple entities would
        // appear at separate Y positions and cause overlapping with NPC name tags.
        net.kyori.adventure.text.Component combined = null;
        for (String line : def.lines()) {
            net.kyori.adventure.text.Component c = Text.c(line);
            combined = (combined == null) ? c : combined.appendNewline().append(c);
        }
        if (combined == null) combined = net.kyori.adventure.text.Component.empty();

        return new TextHologramBuilder(new Pos(def.x(), def.y(), def.z()))
                .line(combined, style)
                .build();
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
